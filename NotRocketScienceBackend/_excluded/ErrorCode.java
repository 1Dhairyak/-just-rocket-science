package com.justrocketscience.gateway.exception;

/**
 * Canonical error codes for jrs-api-gateway.
 *
 * <p>Every error response the gateway returns carries one of these codes in the
 * {@code errorCode} field. This lets frontend clients and other services handle
 * errors programmatically without parsing human-readable message strings.
 *
 * <p><b>Code structure:</b> {@code CATEGORY_SPECIFIC_PROBLEM}
 * <ul>
 *   <li>{@code AUTH_*}   — authentication and JWT errors</li>
 *   <li>{@code ACCESS_*} — authorisation errors</li>
 *   <li>{@code RATE_*}   — rate limiting errors</li>
 *   <li>{@code VAL_*}    — validation errors (request body, params)</li>
 *   <li>{@code GW_*}     — gateway infrastructure errors</li>
 *   <li>{@code ERR_*}    — generic / unexpected errors</li>
 * </ul>
 *
 * <p><b>Interview note:</b> Enum-based error codes are preferable to raw
 * strings because they are refactor-safe, IDE-searchable, and prevent typos.
 * The {@code httpStatus} field on each constant means the handler never has
 * to look up which HTTP status belongs to which error — it is encoded here.
 */
public enum ErrorCode {

    // -------------------------------------------------------------------------
    // Authentication — 401
    // -------------------------------------------------------------------------

    /** No Bearer token present in the Authorization header. */
    AUTH_TOKEN_MISSING(401, "Authentication token is required"),

    /** Token structure is invalid (not a valid JWT format). */
    AUTH_TOKEN_MALFORMED(401, "Authentication token is malformed"),

    /** Token signature does not match — tampered or wrong secret. */
    AUTH_TOKEN_INVALID_SIGNATURE(401, "Authentication token signature is invalid"),

    /** Token has passed its expiry time. */
    AUTH_TOKEN_EXPIRED(401, "Authentication token has expired"),

    /** Token issuer claim does not match expected value. */
    AUTH_TOKEN_INVALID_ISSUER(401, "Authentication token issuer is invalid"),

    /** Token audience claim does not match expected value. */
    AUTH_TOKEN_INVALID_AUDIENCE(401, "Authentication token audience is invalid"),

    /** Token type is not "access" (e.g. a refresh token used as a Bearer). */
    AUTH_TOKEN_WRONG_TYPE(401, "Authentication token type is invalid"),

    /** JTI claim is missing — token cannot be blacklist-checked. */
    AUTH_TOKEN_MISSING_JTI(401, "Authentication token ID is missing"),

    /** Generic JWT validation failure not covered by a specific code above. */
    AUTH_TOKEN_INVALID(401, "Authentication token is invalid"),

    /** Authentication failed for a reason not specific to JWT. */
    AUTH_FAILED(401, "Authentication failed"),

    // -------------------------------------------------------------------------
    // Authorisation — 403
    // -------------------------------------------------------------------------

    /** User is authenticated but does not have the required role/permission. */
    ACCESS_DENIED(403, "You do not have permission to perform this action"),

    // -------------------------------------------------------------------------
    // Rate limiting — 429
    // -------------------------------------------------------------------------

    /** Client has exceeded the anonymous rate limit (keyed by IP). */
    RATE_LIMIT_EXCEEDED_ANONYMOUS(429, "Too many requests — please try again later"),

    /** User has exceeded the authenticated rate limit (keyed by user ID). */
    RATE_LIMIT_EXCEEDED_AUTHENTICATED(429, "Request limit exceeded — please slow down"),

    // -------------------------------------------------------------------------
    // Validation — 400
    // -------------------------------------------------------------------------

    /** Request body failed @Valid / @Validated bean validation. */
    VAL_REQUEST_BODY_INVALID(400, "Request body validation failed"),

    /** One or more request parameters or path variables are invalid. */
    VAL_REQUEST_PARAM_INVALID(400, "Request parameter validation failed"),

    /** Generic bad request — malformed input not covered above. */
    VAL_BAD_REQUEST(400, "Bad request"),

    // -------------------------------------------------------------------------
    // Gateway infrastructure — 502 / 503 / 504
    // -------------------------------------------------------------------------

    /** Circuit breaker is open — downstream service is unavailable. */
    GW_CIRCUIT_OPEN(503, "Service temporarily unavailable — please try again shortly"),

    /** Downstream service did not respond within the configured timeout. */
    GW_TIMEOUT(504, "Upstream service timed out"),

    /** Gateway could not reach the downstream service. */
    GW_UPSTREAM_UNAVAILABLE(502, "Upstream service is unavailable"),

    // -------------------------------------------------------------------------
    // Generic — 500
    // -------------------------------------------------------------------------

    /** An unexpected error occurred. Details are logged server-side only. */
    ERR_INTERNAL(500, "An unexpected error occurred");

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Default HTTP status code associated with this error. */
    private final int defaultHttpStatus;

    /**
     * Human-readable message safe to return to the client.
     * Must never reveal internal implementation details.
     */
    private final String defaultMessage;

    ErrorCode(int defaultHttpStatus, String defaultMessage) {
        this.defaultHttpStatus = defaultHttpStatus;
        this.defaultMessage    = defaultMessage;
    }

    public int    getDefaultHttpStatus() { return defaultHttpStatus; }
    public String getDefaultMessage()    { return defaultMessage; }
}
