package com.justrocketscience.gateway.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7807-style error response body returned by jrs-api-gateway on all errors.
 *
 * <p>Every error the gateway returns — authentication failure, rate limit,
 * validation error, unexpected exception — is serialised as this class.
 * This gives API consumers a single, predictable shape to handle regardless
 * of the error category.
 *
 * <p><b>Wire format example (401 — expired token):</b>
 * <pre>{@code
 * {
 *   "timestamp":     "2024-06-01T12:00:00Z",
 *   "status":        401,
 *   "errorCode":     "AUTH_TOKEN_EXPIRED",
 *   "message":       "Authentication token has expired",
 *   "path":          "/api/v1/rockets",
 *   "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
 * }
 * }</pre>
 *
 * <p><b>Wire format example (400 — validation failure with field errors):</b>
 * <pre>{@code
 * {
 *   "timestamp":     "2024-06-01T12:00:01Z",
 *   "status":        400,
 *   "errorCode":     "VAL_REQUEST_BODY_INVALID",
 *   "message":       "Request body validation failed",
 *   "path":          "/api/v1/rockets",
 *   "correlationId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
 *   "fieldErrors": [
 *     { "field": "name",          "message": "must not be blank" },
 *     { "field": "payloadKg",     "message": "must be greater than 0" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Immutable — built via the static factory method {@link #of} or
 * {@link #withFieldErrors}. No public constructor.
 *
 * <p>{@code @JsonInclude(NON_NULL)} ensures the optional {@code fieldErrors}
 * list is omitted from the response when not applicable, keeping 401/403/429
 * responses lean.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    /** ISO-8601 UTC timestamp of when the error occurred. */
    @JsonProperty("timestamp")
    private final Instant timestamp;

    /** HTTP status code (mirrored in the body for client convenience). */
    @JsonProperty("status")
    private final int status;

    /**
     * Machine-readable error code from {@link ErrorCode}.
     * Clients should switch on this value, not on {@code message}.
     */
    @JsonProperty("errorCode")
    private final String errorCode;

    /**
     * Human-readable message safe for display to end users.
     * Never contains stack traces, SQL errors, or internal service details.
     */
    @JsonProperty("message")
    private final String message;

    /** The request path that produced this error. */
    @JsonProperty("path")
    private final String path;

    /**
     * Correlation ID from the {@code X-Correlation-ID} request header.
     * Allows the client to reference this error when contacting support,
     * and allows server-side log correlation.
     */
    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Optional list of per-field validation errors.
     * Present only for {@code VAL_REQUEST_BODY_INVALID} and
     * {@code VAL_REQUEST_PARAM_INVALID} responses.
     * Omitted from JSON when null.
     */
    @JsonProperty("fieldErrors")
    private final List<FieldError> fieldErrors;

    // -------------------------------------------------------------------------
    // Private constructor — use factory methods
    // -------------------------------------------------------------------------

    private ErrorResponse(Instant timestamp, int status, String errorCode,
                          String message, String path, String correlationId,
                          List<FieldError> fieldErrors) {
        this.timestamp     = timestamp;
        this.status        = status;
        this.errorCode     = errorCode;
        this.message       = message;
        this.path          = path;
        this.correlationId = correlationId;
        this.fieldErrors   = fieldErrors;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a standard error response with no field-level detail.
     * Used for authentication, authorisation, rate-limit, and generic errors.
     */
    public static ErrorResponse of(ErrorCode code, String path, String correlationId) {
        return new ErrorResponse(
                Instant.now(),
                code.getDefaultHttpStatus(),
                code.name(),
                code.getDefaultMessage(),
                path,
                correlationId,
                null
        );
    }

    /**
     * Creates an error response with a custom message override.
     * Use sparingly — the message must still be safe to show to end users.
     * Prefer {@link #of} with the enum's default message when possible.
     */
    public static ErrorResponse of(ErrorCode code, String customMessage,
                                   String path, String correlationId) {
        return new ErrorResponse(
                Instant.now(),
                code.getDefaultHttpStatus(),
                code.name(),
                customMessage,
                path,
                correlationId,
                null
        );
    }

    /**
     * Creates a validation error response that includes per-field error detail.
     * Used for {@code VAL_REQUEST_BODY_INVALID} and
     * {@code VAL_REQUEST_PARAM_INVALID}.
     */
    public static ErrorResponse withFieldErrors(ErrorCode code, String path,
                                                String correlationId,
                                                List<FieldError> fieldErrors) {
        return new ErrorResponse(
                Instant.now(),
                code.getDefaultHttpStatus(),
                code.name(),
                code.getDefaultMessage(),
                path,
                correlationId,
                fieldErrors != null ? List.copyOf(fieldErrors) : List.of()
        );
    }

    // -------------------------------------------------------------------------
    // Getters — required for Jackson serialisation
    // -------------------------------------------------------------------------

    public Instant        getTimestamp()     { return timestamp; }
    public int            getStatus()        { return status; }
    public String         getErrorCode()     { return errorCode; }
    public String         getMessage()       { return message; }
    public String         getPath()          { return path; }
    public String         getCorrelationId() { return correlationId; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }

    // =========================================================================
    // FieldError — nested value object for validation errors
    // =========================================================================

    /**
     * A single per-field validation failure, included in the outer
     * {@code fieldErrors} list for 400 validation responses.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class FieldError {

        @JsonProperty("field")
        private final String field;

        @JsonProperty("message")
        private final String message;

        public FieldError(String field, String message) {
            this.field   = field;
            this.message = message;
        }

        public String getField()   { return field; }
        public String getMessage() { return message; }
    }
}
