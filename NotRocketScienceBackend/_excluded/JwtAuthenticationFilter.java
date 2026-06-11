package com.justrocketscience.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justrocketscience.gateway.security.TokenBlacklistChecker;
import com.justrocketscience.gateway.security.JwtValidator;
import com.justrocketscience.gateway.security.JwtValidator.ClaimsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GlobalFilter that enforces JWT authentication for all protected routes.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Skip authentication for public paths (see {@link #PUBLIC_PATHS}).</li>
 *   <li>Extract the Bearer token from the {@code Authorization} header.</li>
 *   <li>Validate the token via {@link JwtValidator}: signature, issuer, audience,
 *       expiry, and token type ({@code "access"} only — refresh tokens are rejected).</li>
 *   <li>Check the token's JTI against the Redis blacklist via
 *       {@link TokenBlacklistChecker}.</li>
 *   <li>On success: inject {@code X-User-Id}, {@code X-User-Roles}, and
 *       {@code X-Correlation-ID} into the mutated request; strip the raw
 *       {@code Authorization} header before forwarding to the downstream service.</li>
 *   <li>On any failure: terminate the chain immediately with an RFC 7807
 *       {@code application/problem+json} response — no downstream call is made.</li>
 * </ol>
 *
 * <h2>Order</h2>
 * <p>Runs at {@link #ORDER} = {@code 0} — after all logging filters (−200, −150, −100)
 * and before Spring Cloud Gateway's route filters (positive order values).
 *
 * <h2>Why the Authorization header is stripped</h2>
 * <p>Downstream microservices authenticate requests via the pre-validated
 * {@code X-User-Id} and {@code X-User-Roles} headers, not by re-validating the JWT.
 * Forwarding the raw token would:
 * <ul>
 *   <li>Require every service to hold the JWT signing key (breaks the gateway-as-trust-boundary
 *       model).</li>
 *   <li>Log the token in downstream access logs.</li>
 *   <li>Allow a compromised service to replay the token against other services.</li>
 * </ul>
 *
 * <h2>RFC 7807 error shape</h2>
 * <pre>{@code
 * {
 *   "type":     "https://justrocketscience.com/errors/auth/unauthorized",
 *   "title":    "Unauthorized",
 *   "status":   401,
 *   "detail":   "<human-readable reason>",
 *   "instance": "<request path>",
 *   "timestamp": "<ISO-8601>",
 *   "correlationId": "<X-Correlation-ID>"
 * }
 * }</pre>
 *
 * <h2>Security notes</h2>
 * <ul>
 *   <li>Error responses never reveal whether a user ID exists — only that the token is
 *       invalid or missing.</li>
 *   <li>Blacklist check is done <em>after</em> signature validation to avoid unnecessary
 *       Redis hits for structurally invalid tokens.</li>
 *   <li>The filter short-circuits immediately on the first failure — it does not attempt
 *       further validation after detecting a problem.</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final int ORDER = 0;

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // -------------------------------------------------------------------------
    // Public (bypass) paths — must stay in sync with SecurityConfig permit-all list
    // -------------------------------------------------------------------------

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/openapi/**",
            "/fallback/**"
    );

    // -------------------------------------------------------------------------
    // Header names
    // -------------------------------------------------------------------------

    private static final String HEADER_AUTHORIZATION  = HttpHeaders.AUTHORIZATION;
    private static final String HEADER_USER_ID         = "X-User-Id";
    private static final String HEADER_USER_ROLES      = "X-User-Roles";
    private static final String HEADER_CORRELATION     = CorrelationIdFilter.CORRELATION_ID_HEADER;
    private static final String CTX_CORRELATION        = CorrelationIdFilter.CORRELATION_ID_CTX_KEY;

    // -------------------------------------------------------------------------
    // RFC 7807 error type URIs
    // -------------------------------------------------------------------------

    private static final String ERROR_TYPE_BASE        = "https://justrocketscience.com/errors/auth";
    private static final String ERROR_TYPE_UNAUTHORIZED = ERROR_TYPE_BASE + "/unauthorized";
    private static final String ERROR_TYPE_FORBIDDEN    = ERROR_TYPE_BASE + "/forbidden";

    private final JwtValidator           jwtValidator;
    private final TokenBlacklistChecker  blacklistChecker;
    private final ObjectMapper           objectMapper;
    private final AntPathMatcher         pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(
            JwtValidator jwtValidator,
            TokenBlacklistChecker blacklistChecker,
            ObjectMapper objectMapper) {
        this.jwtValidator     = jwtValidator;
        this.blacklistChecker = blacklistChecker;
        this.objectMapper     = objectMapper;
    }

    // =========================================================================
    // Filter entry point
    // =========================================================================

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CTX_CORRELATION, "unknown");

            // 1. Extract Bearer token
            String token = extractBearerToken(exchange);
            if (token == null) {
                log.warn("JWT missing correlationId={} path={}", correlationId, path);
                return rejectUnauthorized(exchange, correlationId,
                        "Authorization header is missing or is not a Bearer token");
            }

            // 2. Validate token structure, signature, claims
            ClaimsResult claimsResult = jwtValidator.validate(token);
            if (!claimsResult.isValid()) {
                log.warn("JWT invalid correlationId={} path={} reason={}",
                        correlationId, path, claimsResult.getFailureReason());
                return rejectUnauthorized(exchange, correlationId, claimsResult.getFailureReason());
            }

            // 3. Check blacklist (reactive — returns Mono<Boolean>)
            return blacklistChecker.isBlacklisted(claimsResult.getJti())
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            log.warn("JWT blacklisted correlationId={} path={} jti={}",
                                    correlationId, path, claimsResult.getJti());
                            return rejectUnauthorized(exchange, correlationId,
                                    "Token has been revoked");
                        }

                        // 4. Inject trusted headers and forward
                        return chain.filter(buildMutatedExchange(exchange, claimsResult, correlationId));
                    });
        });
    }

    // =========================================================================
    // Exchange mutation
    // =========================================================================

    /**
     * Builds a mutated exchange with:
     * <ul>
     *   <li>{@code X-User-Id} injected from the {@code sub} claim</li>
     *   <li>{@code X-User-Roles} injected as a comma-separated list from the {@code roles} claim</li>
     *   <li>{@code X-Correlation-ID} propagated (already set by CorrelationIdFilter, re-set here
     *       to be explicit)</li>
     *   <li>{@code Authorization} header removed — raw token must not reach downstream</li>
     * </ul>
     */
    private ServerWebExchange buildMutatedExchange(
            ServerWebExchange exchange,
            ClaimsResult claims,
            String correlationId) {

        String rolesHeader = String.join(",", claims.getRoles());

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(HEADER_USER_ID, claims.getUserId())
                .header(HEADER_USER_ROLES, rolesHeader)
                .header(HEADER_CORRELATION, correlationId)
                // Strip the raw Authorization header — downstream services must not re-validate.
                // Spring's ServerHttpRequest.mutate() replaces, so passing an empty list removes it.
                .headers(h -> h.remove(HEADER_AUTHORIZATION))
                .build();

        return exchange.mutate().request(mutatedRequest).build();
    }

    // =========================================================================
    // Error responses
    // =========================================================================

    /**
     * Terminates the filter chain with an HTTP 401 response containing an RFC 7807 body.
     *
     * <p>Stores the failure detail in the exchange attribute {@code "gatewayException"} so
     * {@link ResponseLoggingFilter} can include it in the response log line.
     */
    private Mono<Void> rejectUnauthorized(
            ServerWebExchange exchange, String correlationId, String detail) {
        exchange.getAttributes().put("gatewayException",
                new RuntimeException("JWT_UNAUTHORIZED: " + detail));
        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                ERROR_TYPE_UNAUTHORIZED, "Unauthorized", detail, correlationId);
    }

    /**
     * Terminates the filter chain with an HTTP 403 response containing an RFC 7807 body.
     * Reserved for future role-based access control enforcement at the gateway layer.
     */
    @SuppressWarnings("unused")
    private Mono<Void> rejectForbidden(
            ServerWebExchange exchange, String correlationId, String detail) {
        exchange.getAttributes().put("gatewayException",
                new RuntimeException("JWT_FORBIDDEN: " + detail));
        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                ERROR_TYPE_FORBIDDEN, "Forbidden", detail, correlationId);
    }

    /**
     * Writes an RFC 7807 {@code application/problem+json} response to the client and
     * completes the reactive chain.
     */
    private Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            HttpStatus status,
            String type,
            String title,
            String detail,
            String correlationId) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        // Ensure correlation ID is on the error response.
        response.getHeaders().set(HEADER_CORRELATION, correlationId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type",          type);
        body.put("title",         title);
        body.put("status",        status.value());
        body.put("detail",        detail);
        body.put("instance",      exchange.getRequest().getPath().value());
        body.put("timestamp",     Instant.now().toString());
        body.put("correlationId", correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            // Fallback — should never happen with a LinkedHashMap<String, Object>
            bytes = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns {@code true} if the given path matches any of the configured public paths.
     * Uses Ant-style matching so wildcard patterns like {@code /actuator/**} work correctly.
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Extracts the raw JWT from the {@code Authorization: Bearer <token>} header.
     *
     * @return the raw token string, or {@code null} if the header is absent or not a
     *         well-formed Bearer scheme value.
     */
    private String extractBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7).strip();
        return token.isBlank() ? null : token;
    }
}
