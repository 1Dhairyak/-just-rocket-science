package com.justrocketscience.gateway.exception;

/**
 * Base class for all jrs-api-gateway application exceptions.
 *
 * <p>Carries an {@link ErrorCode} so the handler can produce the correct
 * HTTP status and error body without a chain of {@code instanceof} checks.
 *
 * <p>Message is always a client-safe string — never expose internal details.
 */
public class GatewayException extends RuntimeException {

    private final ErrorCode errorCode;

    public GatewayException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GatewayException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}

// =============================================================================
// Concrete exception types — one file for easy navigation
// =============================================================================

/**
 * Thrown by JwtAuthenticationFilter when Bearer token validation fails.
 *
 * <p>Uses a specific {@link ErrorCode} constant so the handler can return
 * a precise error code (e.g. AUTH_TOKEN_EXPIRED vs AUTH_TOKEN_INVALID_SIGNATURE)
 * without parsing exception messages.
 */
class JwtValidationException extends GatewayException {

    public JwtValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

/**
 * Thrown when a request exceeds the configured rate limit.
 *
 * <p>In practice Spring Cloud Gateway's built-in rate limiter returns a 429
 * response directly without throwing. This exception exists so that custom
 * rate-limiting logic (if added later) can integrate with the same handler.
 */
class RateLimitExceededException extends GatewayException {

    public RateLimitExceededException(boolean authenticated) {
        super(
            authenticated
                ? ErrorCode.RATE_LIMIT_EXCEEDED_AUTHENTICATED
                : ErrorCode.RATE_LIMIT_EXCEEDED_ANONYMOUS,
            authenticated
                ? ErrorCode.RATE_LIMIT_EXCEEDED_AUTHENTICATED.getDefaultMessage()
                : ErrorCode.RATE_LIMIT_EXCEEDED_ANONYMOUS.getDefaultMessage()
        );
    }
}
