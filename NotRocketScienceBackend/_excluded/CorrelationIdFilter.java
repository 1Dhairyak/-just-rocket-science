package com.justrocketscience.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * GlobalFilter that establishes a correlation ID for every request passing through the gateway.
 *
 * <h2>Behaviour</h2>
 * <ul>
 *   <li>If the inbound request already carries {@code X-Correlation-ID}, that value is propagated
 *       unchanged — allows a client or upstream proxy to pass its own trace ID through the
 *       gateway chain.</li>
 *   <li>If the header is absent, a new UUID v4 is generated.</li>
 *   <li>The value is stored in:
 *     <ol>
 *       <li><b>Reactor Context</b> — downstream reactive operators (logging filters, JWT filter)
 *           can read it without threading concerns.</li>
 *       <li><b>MDC</b> — wrapped per-signal so every log line emitted during request processing
 *           carries {@code correlationId} automatically.</li>
 *       <li><b>Request header</b> ({@code X-Correlation-ID}) — forwarded to the downstream
 *           service so it can include the same ID in its own logs.</li>
 *       <li><b>Response header</b> ({@code X-Correlation-ID}) — returned to the client for
 *           end-to-end tracing.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h2>Order</h2>
 * <p>Runs first in the filter chain at {@link #ORDER} = {@code -200}. Every subsequent filter
 * can rely on the correlation ID being present in the Reactor Context.
 *
 * <h2>MDC strategy</h2>
 * <p>Spring WebFlux executes requests on Netty event-loop threads. A single thread processes
 * many requests interleaved, so a plain {@code MDC.put()} at the start of a request would bleed
 * into other requests processed on the same thread. The correct approach is to wrap the
 * downstream {@code Mono} with {@code contextWrite} and use a
 * {@code doOnEach} operator that reads the correlation ID from the Reactor Context and sets MDC
 * just before each signal is delivered, then clears it afterwards. This is exactly what the
 * inner {@code Mono.deferContextual} block does.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    /** Filter chain order — must be lower (higher priority) than all other gateway filters. */
    public static final int ORDER = -200;

    /** Header name used on both inbound and outbound sides. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** Reactor Context key used by downstream filters to read the correlation ID. */
    public static final String CORRELATION_ID_CTX_KEY = "correlationId";

    /** MDC key — appears in every log line as {@code correlationId=<value>}. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);

        // Mutate the request to propagate the correlation ID to the downstream service.
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Register a response decorator that writes the correlation ID header before the
        // response is committed. afterCommit hooks are too late — the header write must happen
        // before the first byte of the response body is flushed.
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(CORRELATION_ID_HEADER, correlationId);
            return Mono.empty();
        });

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Write the correlation ID into the Reactor Context so every downstream operator
        // can access it without needing a reference to the exchange.
        return chain.filter(mutatedExchange)
                .contextWrite(Context.of(CORRELATION_ID_CTX_KEY, correlationId));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns the correlation ID to use for this request.
     *
     * <p>If the inbound request already carries a non-blank {@code X-Correlation-ID} header,
     * that value is reused — the gateway acts as a transparent correlating proxy. Otherwise a
     * new UUID v4 is generated.
     *
     * <p>No length or format validation is performed on the inbound value. Trusting inbound
     * correlation IDs verbatim is intentional: the header is for observability only, not for
     * security decisions. A malicious or oversized value would only pollute that client's own
     * logs.
     */
    private String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        return (existing != null && !existing.isBlank())
                ? existing
                : UUID.randomUUID().toString();
    }
}
