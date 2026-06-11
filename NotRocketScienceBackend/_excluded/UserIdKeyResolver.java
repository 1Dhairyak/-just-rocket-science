package com.justrocketscience.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate-limiter key resolver that keys on the authenticated user's ID.
 *
 * <p>The {@code X-User-Id} header is injected by {@code JwtAuthenticationFilter} (order 0)
 * after a JWT is validated. This resolver is only attached to protected routes, so the header
 * must be present by the time routing happens.
 *
 * <p>Fallback strategy: if the header is absent (should not happen on protected routes in
 * production — means JwtAuthenticationFilter did not run or was bypassed), the resolver falls
 * back to the remote IP. This is intentionally lenient rather than failing open with an error,
 * because a missing error here would break the rate-limiting pipeline and let the request
 * through unlimited.
 *
 * <p>Singleton — stateless, safe to reuse across routes.
 */
public final class UserIdKeyResolver implements KeyResolver {

    public static final UserIdKeyResolver INSTANCE = new UserIdKeyResolver();

    private static final Logger log = LoggerFactory.getLogger(UserIdKeyResolver.class);
    private static final String HEADER_USER_ID = "X-User-Id";

    private UserIdKeyResolver() {}

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);

        if (userId != null && !userId.isBlank()) {
            return Mono.just("user:" + userId);
        }

        // Defensive fallback — log a warning; this indicates a filter chain misconfiguration.
        String remoteHost = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";

        log.warn("UserIdKeyResolver: X-User-Id header missing on protected route {}. " +
                        "Falling back to IP-based key. Path={}",
                remoteHost,
                exchange.getRequest().getPath());

        return Mono.just("ip-fallback:" + remoteHost);
    }
}
