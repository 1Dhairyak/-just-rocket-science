package com.justrocketscience.gateway.config;

import com.justrocketscience.gateway.properties.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * WebFlux (reactive) Security configuration for jrs-api-gateway.
 *
 * <h2>Responsibility split</h2>
 * <p>Spring Security handles:
 * <ul>
 *   <li>CORS — the only place in a reactive gateway stack where CORS can be applied
 *       consistently, before route matching. Application-level CORS in Gateway YAML
 *       filters runs after route matching and misses preflight OPTIONS requests that
 *       don't match a route.</li>
 *   <li>Security response headers (CSP, HSTS, frame options, etc.)</li>
 *   <li>Permit-all baseline — actual JWT enforcement is in
 *       {@code JwtAuthenticationFilter} (a {@code GlobalFilter} at order 0), which
 *       gives full control over 401/403 semantics and RFC 7807 error shapes.</li>
 * </ul>
 *
 * <p>Spring Security does NOT handle:
 * <ul>
 *   <li>JWT validation — delegated entirely to {@code JwtAuthenticationFilter}</li>
 *   <li>Session management — gateway is completely stateless; no HTTP session is
 *       ever created</li>
 *   <li>Form login / HTTP Basic — both disabled</li>
 * </ul>
 *
 * <h2>Why permit-all at the Security layer?</h2>
 * <p>Spring Security's {@code authenticated()} check in a reactive gateway would create two
 * parallel auth paths — the Security layer and the JWT filter — which would diverge in error
 * response shape, header injection timing, and blacklist-check ordering. A single filter
 * ({@code JwtAuthenticationFilter}) with full ownership of the auth decision is cleaner and
 * testable in isolation.
 *
 * <h2>CSRF</h2>
 * <p>CSRF protection is disabled. The gateway is a stateless JSON API; there are no HTML
 * form submissions and no cookies carrying session state. CSRF is a browser same-origin attack
 * that requires a session cookie — it does not apply here.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // -------------------------------------------------------------------------
    // Paths permitted without any authentication check at the Security layer.
    // JwtAuthenticationFilter enforces auth separately on the protected subset.
    // -------------------------------------------------------------------------

    /** Spring Boot Actuator base path. */
    private static final String ACTUATOR_PATH       = "/actuator/**";

    /** Springdoc / OpenAPI paths served by the gateway's own springdoc integration. */
    private static final String SWAGGER_UI_PATH     = "/swagger-ui/**";
    private static final String SWAGGER_UI_HTML     = "/swagger-ui.html";
    private static final String API_DOCS_PATH       = "/v3/api-docs/**";
    private static final String WEBJARS_PATH        = "/webjars/**";

    /** Aggregated OpenAPI specs forwarded from downstream services. */
    private static final String OPENAPI_PROXY_PATH  = "/openapi/**";

    /** Auth-service public endpoints — see GatewayRoutesConfig for full comments. */
    private static final String AUTH_REGISTER       = "/api/v1/auth/register";
    private static final String AUTH_LOGIN          = "/api/v1/auth/login";
    private static final String AUTH_REFRESH        = "/api/v1/auth/refresh";

    /** Gateway-owned fallback endpoints. */
    private static final String FALLBACK_PATH       = "/fallback/**";

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    // =========================================================================
    // Security filter chain
    // =========================================================================

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // -----------------------------------------------------------------
                // CSRF — disabled; stateless JSON API, no session cookies
                // -----------------------------------------------------------------
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // -----------------------------------------------------------------
                // CORS — wired from CorsProperties; handles preflight OPTIONS
                //         before route matching
                // -----------------------------------------------------------------
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // -----------------------------------------------------------------
                // Authorization — permit-all at Security layer.
                //
                //   The order matters: more-specific matchers first.
                //   OPTIONS (preflight) must be permitted unconditionally so CORS
                //   handshake completes before any auth check fires.
                //   Everything else is permit-all; JwtAuthenticationFilter owns
                //   the actual auth decision.
                // -----------------------------------------------------------------
                .authorizeExchange(auth -> auth
                        // Preflight — must be first
                        .matchers(ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"))
                                .permitAll()
                        // Actuator — health, metrics, info
                        .pathMatchers(ACTUATOR_PATH).permitAll()
                        // Swagger / OpenAPI — disabled in prod via OpenApiProperties.enabled
                        .pathMatchers(
                                HttpMethod.GET,
                                SWAGGER_UI_PATH,
                                SWAGGER_UI_HTML,
                                API_DOCS_PATH,
                                WEBJARS_PATH,
                                OPENAPI_PROXY_PATH)
                                .permitAll()
                        // Public auth endpoints
                        .pathMatchers(HttpMethod.POST, AUTH_REGISTER, AUTH_LOGIN, AUTH_REFRESH)
                                .permitAll()
                        // Gateway-internal fallbacks
                        .pathMatchers(FALLBACK_PATH).permitAll()
                        // Everything else — permit-all at Security layer;
                        // JwtAuthenticationFilter returns 401 if JWT is absent/invalid.
                        .anyExchange().permitAll())

                // -----------------------------------------------------------------
                // Session — completely stateless; no session ever created
                // -----------------------------------------------------------------
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)

                // -----------------------------------------------------------------
                // Form login / HTTP Basic — disabled; pure Bearer token API
                // -----------------------------------------------------------------
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // -----------------------------------------------------------------
                // Security response headers
                //
                //   Defaults include:
                //   - X-Content-Type-Options: nosniff
                //   - X-Frame-Options: DENY
                //   - X-XSS-Protection: 0  (deprecated, but harmless)
                //
                //   Additional headers configured here:
                //   - HSTS: 1 year, includeSubDomains — gateway is HTTPS-only in prod
                //   - CSP: default-src 'none' — gateway serves no HTML content
                //     (Swagger UI loads its own CSP from webjars)
                //   - Referrer-Policy: no-referrer
                // -----------------------------------------------------------------
                .headers(headers -> headers
                        .hsts(hsts -> hsts
                                .maxAge(java.time.Duration.ofDays(365))
                                .includeSubdomains(true))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyServerHttpHeadersWriter
                                        .ReferrerPolicy.NO_REFERRER)))

                .build();
    }

    // =========================================================================
    // CORS configuration
    // =========================================================================

    /**
     * Builds a {@link CorsConfigurationSource} from {@link CorsProperties}.
     *
     * <p>Applied at the Spring Security layer (before route matching) so that OPTIONS
     * preflight requests that do not match any Gateway route are still handled correctly.
     *
     * <p>Configuration is validated at startup by {@code @NotEmpty} constraints on
     * {@link CorsProperties#getAllowedOrigins()}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(corsProperties.getAllowedOrigins());

        // Allowed methods — default from CorsProperties: GET, POST, PUT, DELETE, PATCH, OPTIONS
        config.setAllowedMethods(
                corsProperties.getAllowedMethods() != null
                        ? corsProperties.getAllowedMethods()
                        : List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Allowed headers — default from CorsProperties: *, but do not allow
        // gateway-internal trust headers to be set by clients.
        config.setAllowedHeaders(
                corsProperties.getAllowedHeaders() != null
                        ? corsProperties.getAllowedHeaders()
                        : List.of("*"));

        // Exposed headers — clients can read these from the response.
        // X-Correlation-ID: for client-side tracing
        // X-Token-Revoked: signals the client to clear its stored tokens
        config.setExposedHeaders(
                corsProperties.getExposedHeaders() != null
                        ? corsProperties.getExposedHeaders()
                        : List.of("X-Correlation-ID", "X-Token-Revoked"));

        // Credentials (cookies / Authorization header) — allow only if origins are explicit.
        // Must not be true when allowedOrigins contains "*".
        config.setAllowCredentials(corsProperties.isAllowCredentials());

        // Preflight cache duration — reduces OPTIONS round-trips.
        // Default 3600s (1h); override in CorsProperties if needed.
        config.setMaxAge(
                corsProperties.getMaxAge() > 0
                        ? corsProperties.getMaxAge()
                        : 3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
