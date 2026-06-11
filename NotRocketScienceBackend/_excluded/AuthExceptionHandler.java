package com.justrocketscience.auth.controller;

import com.justrocketscience.auth.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps all domain and Spring Security exceptions to RFC 7807 Problem JSON responses.
 *
 * <p>Every exception handler in this class:
 * <ul>
 *   <li>Returns a {@link ProblemDetail} (Spring 6 built-in RFC 7807 support)</li>
 *   <li>Sets a {@code type} URI that uniquely identifies the problem type</li>
 *   <li>Sets a {@code timestamp} extension property for log correlation</li>
 *   <li>Never leaks stack traces, internal state, or sensitive values</li>
 * </ul>
 *
 * <p>The controller itself catches nothing — all exceptions propagate here.
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final String TYPE_BASE = "https://api.justrocketscience.com/errors/";

    // ─────────────────────────────────────────────────────────────────────────
    // 400 Bad Request
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles Jakarta Validation failures ({@code @Valid} on request bodies).
     *
     * <p>Returns a structured {@code errors} map of field → message pairs so the
     * frontend can bind error messages to individual form inputs.
     *
     * <p>Class-level constraint violations (e.g. {@code @PasswordsNotEqual}) have
     * {@code field = "newPassword"} by convention (set in the constraint annotation itself).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
            .filter(err -> err instanceof FieldError)
            .map(err -> (FieldError) err)
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                (existing, duplicate) -> existing  // keep first message per field
            ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(TYPE_BASE + "validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields failed validation.");
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(SamePasswordException.class)
    public ProblemDetail handleSamePassword(SamePasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(TYPE_BASE + "same-password"));
        problem.setTitle("Invalid Password");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MalformedAuthHeaderException.class)
    public ProblemDetail handleMalformedAuthHeader(MalformedAuthHeaderException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(TYPE_BASE + "malformed-auth-header"));
        problem.setTitle("Malformed Authorization Header");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 401 Unauthorized
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles wrong credentials and disabled accounts.
     *
     * <p>Both {@link BadCredentialsException} and {@link DisabledException} return the
     * same generic message. Distinguishing them in the response would allow an attacker
     * to confirm that a disabled account exists with valid credentials.
     */
    @ExceptionHandler({ BadCredentialsException.class, DisabledException.class, LockedException.class })
    public ProblemDetail handleAuthenticationFailure(RuntimeException ex) {
        log.debug("Authentication failure: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create(TYPE_BASE + "authentication-failed"));
        problem.setTitle("Authentication Failed");
        // Generic message — does NOT reveal if the account exists or is disabled
        problem.setDetail("Invalid email or password.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create(TYPE_BASE + "invalid-token"));
        problem.setTitle("Invalid Token");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ProblemDetail handleTokenRevoked(TokenRevokedException ex) {
        // Do NOT reveal that reuse was detected — just say invalid token
        log.warn("TOKEN_REUSE_RESPONSE_ISSUED");
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create(TYPE_BASE + "invalid-token"));
        problem.setTitle("Invalid Token");
        problem.setDetail("The refresh token is invalid or has already been used.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ProblemDetail handleAuthRequired(AuthenticationRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create(TYPE_BASE + "authentication-required"));
        problem.setTitle("Authentication Required");
        problem.setDetail("You must be authenticated to access this resource.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 403 Forbidden
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(AccountDisabledException.class)
    public ProblemDetail handleAccountDisabled(AccountDisabledException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create(TYPE_BASE + "account-disabled"));
        problem.setTitle("Account Disabled");
        problem.setDetail("Your account is not active. Please contact support.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create(TYPE_BASE + "user-not-found"));
        problem.setTitle("User Not Found");
        problem.setDetail("The requested user account could not be found.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 409 Conflict
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateUserException.class)
    public ProblemDetail handleDuplicateUser(DuplicateUserException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create(TYPE_BASE + "duplicate-user"));
        problem.setTitle("Account Already Exists");
        // Message is generic — does not specify which field (email vs username) conflicted
        problem.setDetail("An account with that email or username already exists.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 500 Internal Server Error — catch-all
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Catch-all for any unhandled exception.
     *
     * <p>The internal message is logged at ERROR level but is NOT included in the response.
     * Returning internal error messages to clients is a common source of information leakage.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("UNHANDLED_EXCEPTION", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(TYPE_BASE + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
