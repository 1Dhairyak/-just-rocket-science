package com.justrocketscience.gateway.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Circuit breaker configuration for jrs-api-gateway.
 *
 * <p>Bound from the {@code jrs.circuit-breaker.*} namespace in
 * {@code application.yml}.
 *
 * <p>The gateway uses Resilience4j circuit breakers, configured here with
 * beginner-friendly defaults that are reasonable for a student project.
 *
 * <p><b>Circuit breaker states explained simply:</b>
 * <pre>
 *   CLOSED ──(failure rate ≥ threshold)──▶ OPEN
 *     ▲                                      │
 *     │                                (wait-duration)
 *     │                                      │
 *     └──(probe calls succeed)── HALF-OPEN ◀─┘
 * </pre>
 * <ul>
 *   <li><b>CLOSED</b> — normal operation, all requests pass through.</li>
 *   <li><b>OPEN</b> — downstream is unhealthy, requests fail fast with
 *       fallback response. No traffic reaches auth-service.</li>
 *   <li><b>HALF-OPEN</b> — a small number of probe requests are allowed
 *       through to test if the downstream has recovered.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "jrs.circuit-breaker")
public class CircuitBreakerProperties {

    /**
     * Failure rate threshold (percentage) above which the circuit opens.
     * Default: 50% — if half the requests in the sliding window fail,
     * the circuit opens.
     */
    @DecimalMin("10.0")
    @DecimalMax("100.0")
    private float failureRateThreshold = 50.0f;

    /**
     * Minimum number of calls in the sliding window before the failure
     * rate is evaluated. Prevents the circuit from opening after just
     * one or two failures during low traffic.
     * Default: 5 calls.
     */
    @Min(1)
    private int minimumNumberOfCalls = 5;

    /**
     * Size of the sliding window (number of calls) used to evaluate
     * the failure rate.
     * Default: 10 calls.
     */
    @Min(5)
    private int slidingWindowSize = 10;

    /**
     * How long (in seconds) the circuit stays OPEN before transitioning
     * to HALF-OPEN and allowing probe requests.
     * Default: 10 seconds.
     */
    @Min(1)
    private long waitDurationInOpenStateSeconds = 10;

    /**
     * Number of probe calls allowed in HALF-OPEN state.
     * If these succeed, the circuit transitions back to CLOSED.
     * Default: 3 calls.
     */
    @Min(1)
    private int permittedCallsInHalfOpenState = 3;

    /**
     * Timeout (in seconds) for a single downstream call before it is
     * counted as a failure by the circuit breaker.
     * Default: 3 seconds.
     */
    @Min(1)
    private long timeoutDurationSeconds = 3;

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public float getFailureRateThreshold()              { return failureRateThreshold; }
    public void setFailureRateThreshold(float v)        { this.failureRateThreshold = v; }

    public int getMinimumNumberOfCalls()                { return minimumNumberOfCalls; }
    public void setMinimumNumberOfCalls(int v)          { this.minimumNumberOfCalls = v; }

    public int getSlidingWindowSize()                   { return slidingWindowSize; }
    public void setSlidingWindowSize(int v)             { this.slidingWindowSize = v; }

    public long getWaitDurationInOpenStateSeconds()     { return waitDurationInOpenStateSeconds; }
    public void setWaitDurationInOpenStateSeconds(long v) { this.waitDurationInOpenStateSeconds = v; }

    public int getPermittedCallsInHalfOpenState()       { return permittedCallsInHalfOpenState; }
    public void setPermittedCallsInHalfOpenState(int v) { this.permittedCallsInHalfOpenState = v; }

    public long getTimeoutDurationSeconds()             { return timeoutDurationSeconds; }
    public void setTimeoutDurationSeconds(long v)       { this.timeoutDurationSeconds = v; }
}
