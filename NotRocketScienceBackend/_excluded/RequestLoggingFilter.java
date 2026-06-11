package com.justrocketscience.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * GlobalFilter that emits a structured log line for every inbound request.
 *
 * <h2>What is logged</h2>
 * <ul>
 *   <li>{@code correlationId} — from Reactor Context (set by {@link CorrelationIdFilter})</li>
 *   <li>{@code method} — HTTP method</li>
 *   <li>{@code path} — request path (no query string — query params may contain tokens or PII)</li>
 *   <li>{@code clientIp} — resolved via {@code X-Forwarded-For} → {@code X-Real-IP} → TCP</li>
 *   <li>{@code userId} — from {@code X-User-Id} header if already set by an upstream filter;
 *       absent on public routes processed before JWT validation</li>
 *   <li>{@code routeId} — Spring Cloud Gateway route ID if already matched; may be absent on
 *       pre-routing filters</li>
 * </ul>
 *
 * <h2>What is NEVER logged</h2>
 * <ul>
 *   <li>{@code Authorization} header — contains the raw Bearer token</li>
 *   <li>{@code Cookie} header — may contain session identifiers</li>
 *   <li>Request body — bodies may contain credentials (e.g. {@code /login} POST)</li>
 *   <li>Query string — may contain API keys or OAuth tokens passed as query params</li>
 *   <li>Any header whose name contains {@code secret}, {@code token}, {@code key},
 *       or {@code password} (case-insensitive)</li>
 * </ul>
 *
 * <h2>Order</h2>
 * <p>Runs at {@link #ORDER} = {@code -100}, after {@link CorrelationIdFilter} (−200) so the
 * correlation ID is guaranteed to be in the Reactor Context, and before
 * {@link JwtAuthenticationFilter} (0) so the request is logged even if JWT validation fails.
 *
 * <h2>MDC and reactive threading</h2>
 * <p>The log line is emitted inside {@code Mono.deferContextual} so the Reactor Context
 * (including the correlation ID) is accessible without thread-local assumptions. The
 * correlation ID is included in the structured log fields rather than relying on MDC alone,
 * because MDC population happens at the operator level in {@link CorrelationIdFilter}.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -100;

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** Header set by JwtAuthenticationFilter after token validation. */
    private static final String HEADER_USER_ID      = "X-User-Id";
    private static final String HEADER_CORRELATION   = CorrelationIdFilter.CORRELATION_ID_HEADER;
    private static final String CTX_CORRELATION      = CorrelationIdFilter.CORRELATION_ID_CTX_KEY;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CTX_CORRELATION, resolveCorrelationFromHeader(exchange));
            ServerHttpRequest request = exchange.getRequest();

            String method    = request.getMethod().name();
            String path      = request.getPath().value();
            String clientIp  = resolveClientIp(request);
            String userId    = Optional.ofNullable(request.getHeaders().getFirst(HEADER_USER_ID))
                                       .orElse("-");
            String routeId   = resolveRouteId(exchange);

            log.info(">> REQUEST correlationId={} method={} path={} clientIp={} userId={} routeId={}",
                    correlationId, method, path, clientIp, userId, routeId);

            return chain.filter(exchange);
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Resolves the client IP address from the request, preferring proxy-set headers over the
     * raw TCP remote address.
     *
     * <p>Extraction order mirrors {@link com.justrocketscience.gateway.config.IpKeyResolver}:
     * {@code X-Forwarded-For} first value → {@code X-Real-IP} → TCP remote host string.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        String xForwardedFor = headers.getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].strip();
        }

        String xRealIp = headers.getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.strip();
        }

        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getHostString)
                .orElse("unknown");
    }

    /**
     * Reads the matched route ID from the exchange attributes set by Spring Cloud Gateway's
     * route predicate evaluation. Returns {@code "-"} if route matching has not yet occurred
     * (which should not happen at this filter's order, but is defensive).
     */
    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "-";
    }

    /**
     * Fallback correlation ID resolution from the request header, used when the Reactor
     * Context has not yet been populated (should not happen in normal operation, since
     * {@link CorrelationIdFilter} runs first).
     */
    private String resolveCorrelationFromHeader(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION);
        return header != null ? header : "unknown";
    }
}
