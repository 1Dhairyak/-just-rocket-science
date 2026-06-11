package com.justrocketscience.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for jrs-api-gateway.
 *
 * <p>Catches exceptions thrown by gateway controllers and filters, and converts
 * them to a consistent {@link ErrorResponse} body with the correct HTTP status.
 *
 * <p><b>Handler order (most specific → least specific):</b>
 * <ol>
 *   <li>{@link GatewayException} subtypes — our own hierarchy, carries ErrorCode</li>
 *   <li>{@link AuthenticationException} — Spring Security 401</li>
 *   <li>{@link AccessDeniedException} — Spring Security 403</li>
 *   <li>{@link MethodArgumentNotValidException} — @Valid body failures (400)</li>
 *   <li>{@link ConstraintViolationException} — @Validated param failures (400)</li>
 *   <li>{@link ResponseStatusException} — Spring's own status wrapper</li>
 *   <li>{@link IllegalArgumentException} — programming errors surfaced to client</li>
 *   <li>{@link Exception} — catch-all (500)</li>
 * </ol>
 *
 * <p><b>Logging strategy:</b>
 * <ul>
 *   <li>4xx errors — {@code DEBUG} or {@code INFO}. These are client errors,
 *       not platform errors. Logging at WARN/ERROR fills log aggregators with
 *       noise from bots and bad clients.</li>
 *   <li>Signature failures — {@code WARN}. May indicate an attack.</li>
 *   <li>5xx errors — {@code ERROR} with full stack trace. These are platform
 *       bugs and must be investigated.</li>
 * </ul>
 *
 * <p><b>Security considerations:</b>
 * <ul>
 *   <li>No exception message, stack trace, class name, or internal path is
 *       ever included in the response body.</li>
 *   <li>Authentication and authorisation errors return identical structure —
 *       a 401 response never confirms whether a user ID exists.</li>
 *   <li>The correlation ID in the response allows support staff to find the
 *       full server-side log entry without the client needing internal details.</li>
 * </ul>
 *
 * <p><b>WebFlux note:</b> In a reactive gateway, most security enforcement
 * happens in {@code JwtAuthenticationFilter} which writes its own response
 * directly (to avoid reactive context propagation complexity). This handler
 * covers exceptions that escape from controllers and fallback endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 1. Our own exception hierarchy
    // -------------------------------------------------------------------------

    /**
     * Handles all {@link GatewayException} subtypes, including
     * {@link JwtValidationException} and {@link RateLimitExceededException}.
     *
     * <p>The {@link ErrorCode} carried on the exception determines both the
     * HTTP status and the response body — no switch statement needed.
     */
    @ExceptionHandler(GatewayException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGatewayException(
            GatewayException ex, ServerWebExchange exchange) {

        ErrorCode code = ex.getErrorCode();

        // Signature failures may indicate tampered tokens — log at WARN.
        if (code == ErrorCode.AUTH_TOKEN_INVALID_SIGNATURE) {
            log.warn("JWT signature failure — path={}, correlationId={}",
                    path(exchange), correlationId(exchange));
        } else {
            log.debug("GatewayException — code={}, path={}, message={}",
                    code, path(exchange), ex.getMessage());
        }

        ErrorResponse body = ErrorResponse.of(code, path(exchange), correlationId(exchange));
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // 2. Spring Security — AuthenticationException → 401
    // -------------------------------------------------------------------------

    /**
     * Handles Spring Security {@link AuthenticationException}.
     *
     * <p>In most cases this is raised by Spring Security's own filter chain
     * before our JWT filter runs (e.g. missing credentials detected by the
     * security chain before our custom filter). Mapped to 401.
     */
    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAuthenticationException(
            AuthenticationException ex, ServerWebExchange exchange) {

        log.debug("AuthenticationException — path={}, message={}",
                path(exchange), ex.getMessage());

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.AUTH_FAILED, path(exchange), correlationId(exchange));
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // 3. Spring Security — AccessDeniedException → 403
    // -------------------------------------------------------------------------

    /**
     * Handles Spring Security {@link AccessDeniedException}.
     *
     * <p>Raised when a user is authenticated but lacks the required authority.
     * Returning 403 (not 401) is correct — the user is known, just not allowed.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDeniedException(
            AccessDeniedException ex, ServerWebExchange exchange) {

        log.debug("AccessDeniedException — path={}, userId={}",
                path(exchange), exchange.getRequest().getHeaders().getFirst("X-User-Id"));

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.ACCESS_DENIED, path(exchange), correlationId(exchange));
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // 4. Validation — MethodArgumentNotValidException → 400
    // -------------------------------------------------------------------------

    /**
     * Handles @Valid / @Validated failures on request bodies.
     *
     * <p>Collects all field-level errors into the {@code fieldErrors} list so
     * the client can display all problems at once, not just the first one.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, ServerWebExchange exchange) {

        log.debug("Validation failure — path={}, errorCount={}",
                path(exchange), ex.getErrorCount());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponse body = ErrorResponse.withFieldErrors(
                ErrorCode.VAL_REQUEST_BODY_INVALID,
                path(exchange),
                correlationId(exchange),
                fieldErrors);
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // 5. Validation — ConstraintViolationException → 400
    // -------------------------------------------------------------------------

    /**
     * Handles constraint violations from @Validated on method parameters
     * (path variables, request params).
     *
     * <p>Strips the method name prefix from constraint paths
     * (e.g. {@code "getById.id"} → {@code "id"}) for cleaner field names
     * in the response.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolation(
            ConstraintViolationException ex, ServerWebExchange exchange) {

        log.debug("ConstraintViolationException — path={}, violations={}",
                path(exchange), ex.getConstraintViolations().size());

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(cv -> {
                    // "methodName.paramName" → "paramName"
                    String propertyPath = cv.getPropertyPath().toString();
                    String fieldName = propertyPath.contains(".")
                            ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1)
                            : propertyPath;
                    return new ErrorResponse.FieldError(fieldName, cv.getMessage());
                })
                .collect(Collectors.toList());

        ErrorResponse body = ErrorResponse.withFieldErrors(
                ErrorCode.VAL_REQUEST_PARAM_INVALID,
                path(exchange),
                correlationId(exchange),
                fieldErrors);
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // 6. ResponseStatusException — maps Spring's own status wrapper
    // -------------------------------------------------------------------------

    /**
     * Handles {@link ResponseStatusException} thrown by Spring itself or
     * by gateway fallback controllers.
     *
     * <p>Maps the embedded HTTP status to the nearest {@link ErrorCode}:
     * 401 → AUTH_FAILED, 403 → ACCESS_DENIED, 404/405 → VAL_BAD_REQUEST,
     * 429 → RATE_LIMIT_EXCEEDED_ANONYMOUS, 503 → GW_CIRCUIT_OPEN,
     * all others → ERR_INTERNAL.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatusException(
            ResponseStatusException ex, ServerWebExchange exchange) {

        int statusValue = ex.getStatusCode().value();
        log.debug("ResponseStatusException — status={}, path={}", statusValue, path(exchange));

        ErrorCode code = switch (statusValue) {
            case 400 -> ErrorCode.VAL_BAD_REQUEST;
            case 401 -> ErrorCode.AUTH_FAILED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404, 405 -> ErrorCode.VAL_BAD_REQUEST;
            case 429 -> ErrorCode.RATE_LIMIT_EXCEEDED_ANONYMOUS;
            case 503 -> ErrorCode.GW_CIRCUIT_OPEN;
            case 504 -> ErrorCode.GW_TIMEOUT;
            default  -> ErrorCode.ERR_INTERNAL;
        };

        ErrorResponse body = ErrorResponse.of(code, path(exchange), correlationId(exchange));
        return Mono.just(ResponseEntity
                .status(statusValue)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    // -------------------------------------------------------------------------
    // 7. IllegalArgumentException → 400
    // -------------------------------------------------------------------------

    /**
     * Handles {@link IllegalArgumentException}.
     *
     * <p>These often surface from service-layer argument checks. Mapped to 400
     * because they typically indicate a bad client request rather than a
     * platform error. The original message is NOT forwarded — a generic safe
     * message is returned instead.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex, ServerWebExchange exchange) {

        // Log at INFO — more than debug noise but not a platform error.
        log.info("IllegalArgumentException — path={}, message={}", path(exchange), ex.getMessage());

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VAL_BAD_REQUEST, path(exchange), correlationId(exchange));
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // 8. Catch-all — unexpected exceptions → 500
    // -------------------------------------------------------------------------

    /**
     * Catch-all handler for any exception not matched above.
     *
     * <p>Logs at ERROR with the full stack trace (needed for debugging).
     * Returns a generic 500 response — no internal detail is sent to the client.
     *
     * <p>If you see this in production, it indicates an unhandled exception
     * type that should be given its own specific handler.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {

        // ERROR + stack trace — this is a platform bug, not a client error.
        log.error("Unhandled exception — path={}, correlationId={}, type={}",
                path(exchange), correlationId(exchange),
                ex.getClass().getSimpleName(), ex);

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.ERR_INTERNAL, path(exchange), correlationId(exchange));
        return respond(body);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Wraps an {@link ErrorResponse} in a {@link ResponseEntity} with the
     * correct HTTP status and Content-Type header.
     */
    private Mono<ResponseEntity<ErrorResponse>> respond(ErrorResponse body) {
        return Mono.just(ResponseEntity
                .status(body.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    /**
     * Extracts the request path for inclusion in the error response body.
     * Returns "-" if the exchange or path is unavailable.
     */
    private String path(ServerWebExchange exchange) {
        try {
            return exchange.getRequest().getPath().value();
        } catch (Exception e) {
            return "-";
        }
    }

    /**
     * Reads the correlation ID from the request header.
     * {@code CorrelationIdFilter} always sets this header, so it should
     * always be present. Returns "-" as a safe fallback.
     */
    private String correlationId(ServerWebExchange exchange) {
        String id = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        return id != null ? id : "-";
    }
}
