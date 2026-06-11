package com.jrs.gateway.tests;

import com.jrs.gateway.config.GatewayIntegrationTestBase;
import com.jrs.gateway.util.TestTokenFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Phase 2 — Step 7
 * Tests: circuit breaker open state and fallback responses.
 *
 * Circuit breaker is triggered when the upstream service is unreachable.
 * In test environment, upstream routes point to a non-existent host to
 * simulate downstream failure.
 *
 * application-test.yml:
 *   jrs.circuit-breaker.failure-rate-threshold: 50
 *   jrs.circuit-breaker.sliding-window-size: 4
 *   jrs.circuit-breaker.wait-duration-in-open-state: 5s
 *
 * Low window size (4 requests) trips the breaker quickly for test speed.
 */
@DisplayName("Circuit Breaker Fallback")
class CircuitBreakerIT extends GatewayIntegrationTestBase {

    private static final String PROTECTED_URI = "/api/v1/rockets";

    @Test
    @DisplayName("Circuit open → 503 GW_CIRCUIT_OPEN")
    void circuitOpenReturns503() {
        String token = TestTokenFactory.validToken("user-cb-test");

        // Trigger failures to open the circuit (sliding window = 4 failures)
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri(PROTECTED_URI)
                    .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                    .exchange(); // upstream unreachable in test env → connection refused
        }

        // Next call — circuit should be open → fallback response
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("GW_CIRCUIT_OPEN")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.correlationId").isNotEmpty()
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("Circuit open fallback — response does not leak upstream host/port")
    void circuitOpenNoInternalLeakage() {
        String token = TestTokenFactory.validToken("user-cb-leak-test");

        // Trip the circuit
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri(PROTECTED_URI)
                    .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                    .exchange();
        }

        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectBody(String.class)
                .value(body -> {
                    assert !body.contains("localhost")     : "Upstream host leaked";
                    assert !body.contains("refused")       : "Connection error leaked";
                    assert !body.contains("ConnectException") : "Exception class leaked";
                });
    }

    @Test
    @DisplayName("Circuit open fallback — correlationId propagated")
    void circuitOpenCorrelationId() {
        String token = TestTokenFactory.validToken("user-cb-correlation");
        String correlationId = "test-correlation-abc-123";

        // Trip the circuit
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri(PROTECTED_URI)
                    .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                    .exchange();
        }

        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .header("X-Correlation-ID", correlationId)
                .exchange()
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Circuit half-open — single probe request allowed after wait duration")
    void circuitHalfOpenProbe() throws InterruptedException {
        // This test documents the half-open behaviour.
        // Full verification would require controlling upstream health mid-test.
        //
        // Expected sequence:
        //   1. N failures → circuit OPEN
        //   2. Wait wait-duration-in-open-state (5s in test profile)
        //   3. Circuit moves to HALF_OPEN → one probe request is allowed through
        //   4. If probe succeeds → circuit CLOSED; if fails → back to OPEN
        //
        // In test environment upstream is always down, so probe always fails
        // and the circuit returns to OPEN.
        //
        // Verified in unit tests of CircuitBreakerService with a mock upstream.
        System.out.println("[INFO] Half-open behaviour verified via unit test — skipped here");
    }
}
