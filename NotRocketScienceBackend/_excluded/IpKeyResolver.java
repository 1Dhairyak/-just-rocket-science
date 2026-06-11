package com.justrocketscience.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Rate-limiter key resolver that keys on the remote IP address.
 *
 * <p>Used for public (unauthenticated) routes: {@code /register}, {@code /login},
 * {@code /refresh}.
 *
 * <p>Extraction order:
 * <ol>
 *   <li>{@code X-Forwarded-For} first header — populated by a reverse proxy / load balancer.
 *       Only the first value is used; the rest of the chain may have been appended by
 *       intermediate proxies and cannot be trusted.</li>
 *   <li>{@code X-Real-IP} — set by nginx {@code proxy_set_header X-Real-IP $remote_addr}</li>
 *   <li>Raw remote address from the TCP connection — accurate only when the gateway is directly
 *       internet-facing (no proxy).</li>
 * </ol>
 *
 * <p>Caveat: if the gateway is behind a proxy that does not set {@code X-Forwarded-For},
 * all clients will share the proxy's IP as the key. Ensure the upstream proxy is configured
 * correctly, or the rate limiter will treat all traffic as a single client.
 *
 * <p>Singleton — stateless, safe to reuse across routes.
 */
public final class IpKeyResolver implements KeyResolver {

    public static final IpKeyResolver INSTANCE = new IpKeyResolver();

    private IpKeyResolver() {}

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Only trust the leftmost entry — the client IP before any proxy appended its address.
            String clientIp = xForwardedFor.split(",")[0].strip();
            return Mono.just("ip:" + clientIp);
        }

        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return Mono.just("ip:" + xRealIp.strip());
        }

        return Mono.just("ip:" + Optional.ofNullable(
                        exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getHostString)
                .orElse("unknown"));
    }
}
