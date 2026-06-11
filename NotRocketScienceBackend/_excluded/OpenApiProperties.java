package com.justrocketscience.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAPI / Swagger aggregation properties for jrs-api-gateway.
 *
 * <p>Bound from the {@code jrs.openapi.*} namespace in {@code application.yml}.
 *
 * <p>The gateway aggregates OpenAPI specs from all downstream services into a
 * single Swagger UI. Each downstream service exposes its own spec at
 * {@code /v3/api-docs}. The gateway routes these through named paths under
 * {@code /openapi/{service-name}/v3/api-docs} and registers them with
 * springdoc-openapi's group configuration.
 *
 * <p>To add a new service to the Swagger UI, add an entry to the
 * {@code services} map in {@code application.yml}. No Java code changes needed.
 *
 * <p><b>How it works:</b>
 * <pre>
 *   Browser → GET /openapi/auth-service/v3/api-docs
 *           → Gateway route forwards to lb://jrs-auth-service/v3/api-docs
 *           → Response returned to Swagger UI
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "jrs.openapi")
public class OpenApiProperties {

    /**
     * Title shown in the Swagger UI header.
     * Default: "Just Rocket Science API"
     */
    @NotBlank
    private String title = "Just Rocket Science API";

    /**
     * Short description shown below the title in Swagger UI.
     */
    private String description =
            "Cloud-native Space Vehicle Analytics Platform — API Gateway";

    /**
     * API version string shown in Swagger UI.
     * Default: "1.0.0"
     */
    @NotBlank
    private String version = "1.0.0";

    /**
     * Map of downstream services registered in the Swagger UI.
     *
     * <p>Key: display name shown as a group label in Swagger UI
     *        (e.g. "Auth Service").
     * <p>Value: the URL path prefix used in the gateway route
     *        (e.g. "/openapi/auth-service/v3/api-docs").
     *
     * <p>Example {@code application.yml} entry:
     * <pre>
     * jrs:
     *   openapi:
     *     services:
     *       "Auth Service": "/openapi/auth-service/v3/api-docs"
     *       "Rocket Service": "/openapi/rocket-service/v3/api-docs"
     * </pre>
     */
    private Map<String, String> services = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getTitle()                    { return title; }
    public void setTitle(String v)              { this.title = v; }

    public String getDescription()              { return description; }
    public void setDescription(String v)        { this.description = v; }

    public String getVersion()                  { return version; }
    public void setVersion(String v)            { this.version = v; }

    public Map<String, String> getServices()    { return services; }
    public void setServices(Map<String, String> v) { this.services = v; }
}
