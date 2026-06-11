package com.justrocketscience.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * GlobalFilter that emits a structured log line for every response leaving the gateway.
 *
 * <h2>What is logged</h2>
 * <ul>
 *   <li>{@code correlationId} — from Reactor Context</li>
 *   <li>{@code status} — HTTP response status code</li>
 *   <li>{@code latencyMs} — wall-clock time from filter entry to response completion</li>
 *   <li>{@code routeId} — matched route ID; may be {@code "-"} on routes that were
 *       rejected before matching (e.g. 429 from rate limiter)</li>
 *   <li>{@code exception} — exception class name + message if a {@link Throwable} was
 *       stored in the exchange attributes by a downstream filter or the route handler</li>
 * </ul>
 *
 * <h2>Order</h2>
 * <p>Runs at {@link #ORDER} = {@code -150}, between {@link CorrelationIdFilter} (−200) and
 * {@link RequestLoggingFilter} (−100). This placement means ResponseLoggingFilter wraps
 * all subsequent filters and the downstream call, so {@code latencyMs} includes JWT
 * validation, rate limiter, circuit breaker, and the actual downstream round-trip.
 *
 * <p>The response log line is emitted in {@code doFinally} so it fires regardless of whether
 * the downstream Mono completes normally, errors, or is cancelled (client disconnect).
 */
@Component
public class ResponseLoggingFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -150;

    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingFilter.class);

    private static final String CTX_CORRELATION = CorrelationIdFilter.CORRELATION_ID_CTX_KEY;

    /**
     * Exchange attribute key under which Spring Cloud Gateway (and our own
     * {@link JwtAuthenticationFilter}) store exceptions for downstream propagation.
     */
    private static final String EXCEPTION_ATTR = ServerWebExchangeUtils.HYSTRIX_EXECUTION_EXCEPTION_ATTR;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();

        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CTX_CORRELATION, "unknown");

            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

                        HttpStatus status = exchange.getResponse().getStatusCode() != null
                                ? (HttpStatus) exchange.getResponse().getStatusCode()
                                : HttpStatus.INTERNAL_SERVER_ERROR;

                        String routeId = resolveRouteId(exchange);
                        String exceptionInfo = resolveException(exchange);

                        if (status.isError()) {
                            log.warn("<< RESPONSE correlationId={} status={} latencyMs={} routeId={} signal={} exception={}",
                                    correlationId, status.value(), latencyMs, routeId,
                                    signalType, exceptionInfo);
                        } else {
                            log.info("<< RESPONSE correlationId={} status={} latencyMs={} routeId={} signal={}",
                                    correlationId, status.value(), latencyMs, routeId, signalType);
                        }
                    });
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "-";
    }

    /**
     * Resolves exception details from the exchange attributes.
     *
     * <p>Spring Cloud Gateway stores circuit-breaker and filter exceptions in
     * {@link ServerWebExchangeUtils#HYSTRIX_EXECUTION_EXCEPTION_ATTR}. We also check a
     * custom attribute {@code "gatewayException"} that {@link JwtAuthenticationFilter} sets
     * when it short-circuits the chain with a 401/403.
     */
    private String resolveException(ServerWebExchange exchange) {
        Throwable t = exchange.getAttribute(EXCEPTION_ATTR);
        if (t == null) {
            t = exchange.getAttribute("gatewayException");
        }
        if (t == null) {
            return "-";
        }
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}
