package com.justrocketscience.gateway.config;

import com.justrocketscience.gateway.properties.CircuitBreakerProperties;
import com.justrocketscience.gateway.properties.RateLimitProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * Programmatic route definitions for jrs-api-gateway.
 *
 * <p>Route priority (evaluated top-to-bottom by Spring Cloud Gateway):
 * <ol>
 *   <li>OpenAPI spec forwarding — exact paths, no auth, no rate limit</li>
 *   <li>Public auth routes — IP-keyed rate limiter, no JWT required</li>
 *   <li>Protected auth routes — user-keyed rate limiter, JWT required (enforced by
 *       JwtAuthenticationFilter)</li>
 * </ol>
 *
 * <p>All routes use {@code lb://} URIs so a DiscoveryClient can be added with no route changes.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Programmatic routes over YAML: type-safe, fully testable, no string key typos</li>
 *   <li>{@code RemoveRequestHeader} for trusted internal headers applied at the route level for
 *       routes that forward to downstream services; default-filters in application.yml handle the
 *       gateway-wide strip</li>
 *   <li>Circuit breaker fallbacks use {@code forward:/fallback/**} — keeps fallback logic inside
 *       the gateway process, no extra network hop</li>
 *   <li>Rate limiters reference beans by name rather than constructing inline — allows the same
 *       limiter bean to be reused across routes and makes per-tier tuning trivial</li>
 * </ul>
 */
@Configuration
public class GatewayRoutesConfig {

    // -------------------------------------------------------------------------
    // Route IDs — constants prevent silent typos in tests and health endpoints
    // -------------------------------------------------------------------------

    public static final String ROUTE_OPENAPI_AUTH        = "openapi-auth-service";
    public static final String ROUTE_AUTH_REGISTER       = "auth-register";
    public static final String ROUTE_AUTH_LOGIN          = "auth-login";
    public static final String ROUTE_AUTH_REFRESH        = "auth-refresh";
    public static final String ROUTE_AUTH_PROTECTED      = "auth-protected";

    private static final String AUTH_SERVICE_LB          = "lb://jrs-auth-service";
    private static final String FALLBACK_AUTH_URI        = "forward:/fallback/auth";

    private final RateLimitProperties   rateLimitProps;
    private final CircuitBreakerProperties cbProps;

    public GatewayRoutesConfig(
            RateLimitProperties rateLimitProps,
            CircuitBreakerProperties cbProps) {
        this.rateLimitProps = rateLimitProps;
        this.cbProps        = cbProps;
    }

    // =========================================================================
    // Route Locator
    // =========================================================================

    @Bean
    public RouteLocator gatewayRouteLocator(
            RouteLocatorBuilder builder,
            RedisRateLimiter anonymousRateLimiter,
            RedisRateLimiter authenticatedRateLimiter) {

        return builder.routes()

                // -----------------------------------------------------------------
                // 1. OpenAPI spec forwarding — auth-service /v3/api-docs
                //
                //    Matched before all other routes.
                //    No rate limiter, no circuit breaker — spec is small + cached.
                //    Swagger UI calls this path to populate the aggregate UI.
                //    Path rewrite: /openapi/auth-service/v3/api-docs
                //                       → /v3/api-docs (on auth-service)
                // -----------------------------------------------------------------
                .route(ROUTE_OPENAPI_AUTH, r -> r
                        .path("/openapi/auth-service/v3/api-docs")
                        .filters(f -> f
                                .rewritePath(
                                        "/openapi/auth-service(?<segment>.*)",
                                        "${segment}")
                                // Prevent downstream from being hit with gateway-injected
                                // internal headers on a public, unauthenticated spec request.
                                .removeRequestHeader("X-User-Id")
                                .removeRequestHeader("X-User-Roles")
                                .removeRequestHeader("X-Internal-Token"))
                        .uri(AUTH_SERVICE_LB))

                // -----------------------------------------------------------------
                // 2a. POST /api/v1/auth/register — public, IP rate-limited
                //
                //    Restricted to POST only: GET /register would be a 404 on the
                //    downstream service anyway, but limiting the method here prevents
                //    the rate-limit slot from being consumed by scanner noise.
                // -----------------------------------------------------------------
                .route(ROUTE_AUTH_REGISTER, r -> r
                        .path("/api/v1/auth/register")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(anonymousRateLimiter)
                                        .setKeyResolver(IpKeyResolver.INSTANCE)
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS))
                                .circuitBreaker(c -> c
                                        .setName(authCircuitBreakerName())
                                        .setFallbackUri(FALLBACK_AUTH_URI)))
                        .uri(AUTH_SERVICE_LB))

                // -----------------------------------------------------------------
                // 2b. POST /api/v1/auth/login — public, IP rate-limited
                //
                //    Intentionally tighter than /register in practice — the anonymous
                //    tier is configured in RateLimitProperties.  Brute-force of login
                //    is the highest-risk endpoint; the anonymous tier (10 req/min burst
                //    20) is intentionally low for this reason.
                // -----------------------------------------------------------------
                .route(ROUTE_AUTH_LOGIN, r -> r
                        .path("/api/v1/auth/login")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(anonymousRateLimiter)
                                        .setKeyResolver(IpKeyResolver.INSTANCE)
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS))
                                .circuitBreaker(c -> c
                                        .setName(authCircuitBreakerName())
                                        .setFallbackUri(FALLBACK_AUTH_URI)))
                        .uri(AUTH_SERVICE_LB))

                // -----------------------------------------------------------------
                // 2c. POST /api/v1/auth/refresh — public, IP rate-limited
                //
                //    Refresh is public because clients call it with an expired access
                //    token — they have no valid JWT to key the user-tier limiter on.
                //    IP-based limiting still applies.
                // -----------------------------------------------------------------
                .route(ROUTE_AUTH_REFRESH, r -> r
                        .path("/api/v1/auth/refresh")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(anonymousRateLimiter)
                                        .setKeyResolver(IpKeyResolver.INSTANCE)
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS))
                                .circuitBreaker(c -> c
                                        .setName(authCircuitBreakerName())
                                        .setFallbackUri(FALLBACK_AUTH_URI)))
                        .uri(AUTH_SERVICE_LB))

                // -----------------------------------------------------------------
                // 3. /api/v1/auth/** — protected catch-all
                //
                //    Matches everything under /api/v1/auth/ that wasn't matched above
                //    (register, login, refresh).  JWT validation is enforced upstream
                //    by JwtAuthenticationFilter (order 0).  This route only handles
                //    routing and rate-limiting; it does NOT perform auth itself.
                //
                //    Key resolver: user ID from X-User-Id header, injected by
                //    JwtAuthenticationFilter after successful token validation.
                //    If the header is absent (request reached here without a valid
                //    JWT), the key resolver falls back to IP — this should never
                //    happen in practice because JwtAuthenticationFilter returns 401
                //    before the request reaches the routing layer.
                // -----------------------------------------------------------------
                .route(ROUTE_AUTH_PROTECTED, r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(authenticatedRateLimiter)
                                        .setKeyResolver(UserIdKeyResolver.INSTANCE)
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS))
                                .circuitBreaker(c -> c
                                        .setName(authCircuitBreakerName())
                                        .setFallbackUri(FALLBACK_AUTH_URI))
                                // Strip auth header before forwarding — downstream services
                                // validate the pre-validated X-User-Id header instead.
                                .removeRequestHeader("Authorization"))
                        .uri(AUTH_SERVICE_LB))

                .build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns the Resilience4j circuit breaker name for auth-service.
     * Must match the key in {@code jrs.circuit-breaker.services} in application.yml
     * and in {@link CircuitBreakerProperties}.
     */
    private String authCircuitBreakerName() {
        return "auth-service";
    }
}
