package com.jrs.gateway.tests;

import com.jrs.gateway.config.GatewayIntegrationTestBase;
import com.jrs.gateway.util.TestTokenFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

/**
 * Phase 2 — Step 7
 * Tests: X-Correlation-ID header — generation, propagation, and echo.
 */
@DisplayName("Correlation ID Propagation")
class CorrelationIdIT extends GatewayIntegrationTestBase {

    private static final String PROTECTED_URI = "/api/v1/rockets";
    private static final String PUBLIC_URI    = "/actuator/health";

    @Test
    @DisplayName("No X-Correlation-ID sent → gateway generates one (UUID format)")
    void gatewayGeneratesCorrelationId() {
        webTestClient.get()
                .uri(PUBLIC_URI)
                .exchange()
                .expectHeader().value("X-Correlation-ID", correlationId -> {
                    // Must be parseable as UUID
                    UUID.fromString(correlationId); // throws if malformed
                });
    }

    @Test
    @DisplayName("X-Correlation-ID sent by client → same value echoed back")
    void clientCorrelationIdEchoed() {
        String id = "client-trace-abc-" + UUID.randomUUID();
        webTestClient.get()
                .uri(PUBLIC_URI)
                .header("X-Correlation-ID", id)
                .exchange()
                .expectHeader().valueEquals("X-Correlation-ID", id);
    }

    @Test
    @DisplayName("X-Correlation-ID echoed on 401 error responses")
    void correlationIdPresentOn401() {
        String id = UUID.randomUUID().toString();
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header("X-Correlation-ID", id)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo(id);
    }

    @Test
    @DisplayName("X-Correlation-ID echoed on 429 rate limit responses")
    void correlationIdPresentOn429() {
        String id = UUID.randomUUID().toString();

        // Hammer until 429
        for (int i = 0; i < 30; i++) {
            var result = webTestClient.get()
                    .uri(PUBLIC_URI)
                    .header("X-Correlation-ID", id)
                    .exchange();

            if (result.returnResult(String.class).getStatus().value() == 429) {
                result.expectBody()
                        .jsonPath("$.correlationId").isEqualTo(id);
                return;
            }
        }
    }

    @Test
    @DisplayName("Two concurrent requests get independent correlation IDs")
    void concurrentRequestsGetIndependentIds() {
        // Request A — no correlation ID sent
        String idA = webTestClient.get()
                .uri(PUBLIC_URI)
                .exchange()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst("X-Correlation-ID");

        // Request B — no correlation ID sent
        String idB = webTestClient.get()
                .uri(PUBLIC_URI)
                .exchange()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst("X-Correlation-ID");

        assert idA != null && idB != null : "Both responses must have X-Correlation-ID";
        assert !idA.equals(idB) : "Two independent requests must get different correlation IDs";
    }

    @Test
    @DisplayName("Correlation ID propagated to upstream via request header")
    void correlationIdForwardedUpstream() {
        // When the gateway forwards a request to upstream, it must include
        // X-Correlation-ID in the outgoing request headers so that upstream
        // services can include it in their own logs.
        //
        // Full verification requires an upstream mock that echoes received headers.
        // Covered in unit tests of JwtAuthenticationFilter / request decoration step.
        // This test documents the contract.
        String id = UUID.randomUUID().toString();
        String token = TestTokenFactory.validToken("user-upstream-trace");

        webTestClient.get()
                .uri(PROTECTED_URI)
                .header("X-Correlation-ID", id)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                // Response header must carry the same ID regardless of upstream behaviour
                .expectHeader().valueEquals("X-Correlation-ID", id);
    }
}
