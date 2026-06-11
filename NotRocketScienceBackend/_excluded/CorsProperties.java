package com.justrocketscience.gateway.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CORS configuration properties for jrs-api-gateway.
 *
 * <p>Bound from the {@code jrs.cors.*} namespace in {@code application.yml}.
 *
 * <p>These values are read by {@code SecurityConfig} to build the
 * {@link org.springframework.web.cors.CorsConfiguration} applied to all routes.
 *
 * <p>During local development, {@code allowed-origins} typically contains
 * {@code http://localhost:3000}. In AWS production, replace this with your
 * CloudFront distribution URL or the domain of your frontend.
 */
@Validated
@ConfigurationProperties(prefix = "jrs.cors")
public class CorsProperties {

    /**
     * Origins that are permitted to make cross-origin requests.
     * Example: ["http://localhost:3000", "https://justrocketscience.com"]
     * Use ["*"] only in development — never in production.
     */
    @NotEmpty(message = "At least one allowed origin must be configured")
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    /**
     * HTTP methods allowed in CORS requests.
     * Default covers all methods used by a REST API.
     */
    private List<String> allowedMethods =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * HTTP headers the client is permitted to send.
     * {@code Authorization} and {@code Content-Type} are required for JWT-based APIs.
     * {@code X-Correlation-ID} allows clients to pass their own trace ID.
     */
    private List<String> allowedHeaders =
            List.of("Authorization", "Content-Type", "X-Correlation-ID");

    /**
     * Headers the browser is allowed to read from the response.
     * {@code X-Correlation-ID} is exposed so frontend tooling can log it.
     */
    private List<String> exposedHeaders = List.of("X-Correlation-ID");

    /**
     * Whether the browser should include credentials (cookies, auth headers)
     * in cross-origin requests. Set to {@code true} only if your frontend
     * explicitly needs to send credentials.
     * Default: false — most REST API frontends don't need this.
     */
    private boolean allowCredentials = false;

    /**
     * How long (in seconds) the browser can cache preflight responses.
     * Default: 3600 (1 hour) — reduces OPTIONS round-trips in production.
     */
    private long maxAge = 3600;

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public List<String> getAllowedOrigins()             { return allowedOrigins; }
    public void setAllowedOrigins(List<String> v)       { this.allowedOrigins = v; }

    public List<String> getAllowedMethods()             { return allowedMethods; }
    public void setAllowedMethods(List<String> v)       { this.allowedMethods = v; }

    public List<String> getAllowedHeaders()             { return allowedHeaders; }
    public void setAllowedHeaders(List<String> v)       { this.allowedHeaders = v; }

    public List<String> getExposedHeaders()             { return exposedHeaders; }
    public void setExposedHeaders(List<String> v)       { this.exposedHeaders = v; }

    public boolean isAllowCredentials()                 { return allowCredentials; }
    public void setAllowCredentials(boolean v)          { this.allowCredentials = v; }

    public long getMaxAge()                             { return maxAge; }
    public void setMaxAge(long v)                       { this.maxAge = v; }
}
