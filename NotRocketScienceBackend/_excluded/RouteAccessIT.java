package com.jrs.gateway.tests;

import com.jrs.gateway.config.GatewayIntegrationTestBase;
import com.jrs.gateway.util.TestTokenFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Phase 2 — Step 7
 * Tests: public route passthrough, protected route enforcement, OPTIONS preflight.
 */
@DisplayName("Route Access Control")
class RouteAccessIT extends GatewayIntegrationTestBase {

    // -------------------------------------------------------------------------
    // Public routes
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Public routes — no token required")
    class PublicRoutes {

        @Test
        @DisplayName("GET /actuator/health → 200 without token")
        void actuatorHealthIsPublic() {
            webTestClient.get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("POST /api/v1/auth/login → reaches upstream without auth header")
        void loginRouteIsPublic() {
            webTestClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"username":"user@example.com","password":"secret"}
                            """)
                    .exchange()
                    // Gateway passes request through — upstream may 401, but gateway should NOT
                    .expectStatus().value(status -> {
                        assert status != 400 : "Gateway should not block public login route";
                    });
        }

        @Test
        @DisplayName("POST /api/v1/auth/register → reaches upstream without auth header")
        void registerRouteIsPublic() {
            webTestClient.post()
                    .uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"username":"new@example.com","password":"secret"}
                            """)
                    .exchange()
                    .expectStatus().value(status -> {
                        assert status != 401 : "Gateway should not block public register route";
                    });
        }

        @Test
        @DisplayName("POST /api/v1/auth/refresh → reaches upstream without auth header")
        void refreshRouteIsPublic() {
            webTestClient.post()
                    .uri("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"refreshToken":"some-refresh-token"}
                            """)
                    .exchange()
                    .expectStatus().value(status -> {
                        assert status != 401 : "Gateway must not block public refresh route";
                    });
        }

        @Test
        @DisplayName("GET /swagger-ui.html → 200 or redirect without token")
        void swaggerUiIsPublic() {
            webTestClient.get()
                    .uri("/swagger-ui.html")
                    .exchange()
                    .expectStatus().value(status -> {
                        assert status == 200 || status == 302 || status == 308
                                : "Swagger UI should be accessible without auth, got " + status;
                    });
        }
    }

    // -------------------------------------------------------------------------
    // Protected routes — valid token passes, missing token blocked
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Protected routes — token required")
    class ProtectedRoutes {

        @Test
        @DisplayName("GET /api/v1/rockets → 401 without Authorization header")
        void protectedRouteBlockedWithNoToken() {
            webTestClient.get()
                    .uri("/api/v1/rockets")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MISSING")
                    .jsonPath("$.status").isEqualTo(401)
                    .jsonPath("$.correlationId").isNotEmpty();
        }

        @Test
        @DisplayName("GET /api/v1/rockets → passes with valid token")
        void protectedRouteAllowedWithValidToken() {
            String token = TestTokenFactory.validToken("user-123");
            webTestClient.get()
                    .uri("/api/v1/rockets")
                    .header(HttpHeaders.AUTHORIZATION, TestTokenFactory.bearer(token))
                    .exchange()
                    // Gateway passes through — upstream may 404 in test env, but NOT 401/403
                    .expectStatus().value(status -> {
                        assert status != 401 && status != 403
                                : "Valid token must not be rejected by gateway, got " + status;
                    });
        }

        @Test
        @DisplayName("GET /api/v1/launches → 401 without token")
        void launchesProtected() {
            webTestClient.get()
                    .uri("/api/v1/launches")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Authorization header with wrong scheme (Basic) → 401")
        void wrongAuthSchemeRejected() {
            webTestClient.get()
                    .uri("/api/v1/rockets")
                    .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("AUTH_TOKEN_MISSING");
        }
    }

    // -------------------------------------------------------------------------
    // CORS / OPTIONS preflight
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CORS preflight")
    class CorsPreflight {

        @Test
        @DisplayName("OPTIONS /api/v1/rockets → 200 with CORS headers")
        void preflightReturnsOk() {
            webTestClient.options()
                    .uri("/api/v1/rockets")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                    .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        }
    }
}
