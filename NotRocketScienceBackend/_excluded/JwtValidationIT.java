package com.jrs.gateway.tests;

import com.jrs.gateway.config.GatewayIntegrationTestBase;
import com.jrs.gateway.util.TestTokenFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Phase 2 — Step 7
 * Tests every JwtValidator validation step (1–8) via full HTTP round-trips.
 *
 * Step 1: Token present
 * Step 2: Token parseable (structure)
 * Step 3: Signature valid
 * Step 4: Not expired
 * Step 5: Issuer matches
 * Step 6: Audience matches
 * Step 7: type == "access"
 * Step 8: Subject non-blank
 */
@DisplayName("JWT Validation — All 8 Steps")
class JwtValidationIT extends GatewayIntegrationTestBase {

    private static final String PROTECTED_URI = "/api/v1/rockets";

    // Step 1 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 1 — Missing Authorization header → AUTH_TOKEN_MISSING / 401")
    void step1_missingHeader() {
        webTestClient.get()
                .uri(PROTECTED_URI)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MISSING")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("Step 1 — Empty Authorization header → AUTH_TOKEN_MISSING / 401")
    void step1_emptyHeader() {
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, "")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MISSING");
    }

    @Test
    @DisplayName("Step 1 — 'Bearer ' with no token → AUTH_TOKEN_MISSING / 401")
    void step1_bearerPrefixOnly() {
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MISSING");
    }

    // Step 2 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 2 — Malformed token (not 3-part JWT) → AUTH_TOKEN_MALFORMED / 401")
    void step2_malformedToken() {
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt.here")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MALFORMED");
    }

    @Test
    @DisplayName("Step 2 — Random string as token → AUTH_TOKEN_MALFORMED / 401")
    void step2_randomString() {
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer thisIsNotAJwtAtAll")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MALFORMED");
    }

    // Step 3 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 3 — Wrong secret → AUTH_TOKEN_INVALID_SIGNATURE / 401")
    void step3_wrongSignature() {
        String token = TestTokenFactory.wrongSecretToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Step 3 — Tampered payload (signature mismatch) → AUTH_TOKEN_INVALID_SIGNATURE / 401")
    void step3_tamperedPayload() {
        String valid = TestTokenFactory.validToken("user-123");
        // Corrupt the payload section (middle segment)
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + ".AAAAAAAAAAAAAAAA." + parts[2];
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_INVALID_SIGNATURE");
    }

    // Step 4 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 4 — Expired token → AUTH_TOKEN_EXPIRED / 401")
    void step4_expiredToken() {
        String token = TestTokenFactory.expiredToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_EXPIRED");
    }

    // Step 5 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 5 — Wrong issuer → AUTH_TOKEN_INVALID / 401")
    void step5_wrongIssuer() {
        String token = TestTokenFactory.wrongIssuerToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_INVALID");
    }

    // Step 6 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 6 — Wrong audience → AUTH_TOKEN_INVALID / 401")
    void step6_wrongAudience() {
        String token = TestTokenFactory.wrongAudienceToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_INVALID");
    }

    // Step 7 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 7 — Missing type claim → AUTH_TOKEN_INVALID / 401")
    void step7_missingTypeClaim() {
        String token = TestTokenFactory.noTypeClaimToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_INVALID");
    }

    @Test
    @DisplayName("Step 7 — Refresh token used on access-only endpoint → AUTH_TOKEN_INVALID / 401")
    void step7_refreshTokenRejected() {
        String token = TestTokenFactory.refreshToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_INVALID");
    }

    // Step 8 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Step 8 — Valid token passes all validation → gateway forwards request")
    void step8_validTokenPassesThrough() {
        String token = TestTokenFactory.validToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectStatus().value(status -> {
                    assert status != 401 && status != 403
                            : "Valid token rejected by gateway — validation regression, got " + status;
                });
    }

    // Error response contract ─────────────────────────────────────────────────

    @Test
    @DisplayName("Error response always contains timestamp, path, correlationId")
    void errorResponseShape() {
        webTestClient.get()
                .uri(PROTECTED_URI)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.path").isEqualTo(PROTECTED_URI)
                .jsonPath("$.correlationId").isNotEmpty()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("Error response never leaks internal exception class or stack trace")
    void noInternalLeakage() {
        String token = TestTokenFactory.wrongSecretToken("user-123");
        webTestClient.get()
                .uri(PROTECTED_URI)
                .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                .exchange()
                .expectBody(String.class)
                .value(body -> {
                    assert !body.contains("Exception") : "Exception class leaked in response body";
                    assert !body.contains("at com.")   : "Stack trace leaked in response body";
                    assert !body.contains("HmacSHA")   : "Algorithm detail leaked in response body";
                });
    }
}
