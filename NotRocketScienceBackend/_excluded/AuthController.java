package com.justrocketscience.auth.controller;

import com.justrocketscience.auth.dto.*;
import com.justrocketscience.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for all authentication endpoints.
 *
 * <p>Responsibilities of this layer:
 * <ul>
 *   <li>HTTP mapping — methods, paths, status codes, headers</li>
 *   <li>Request deserialization and {@code @Valid} delegation to Jakarta Validation</li>
 *   <li>Raw token extraction from the {@code Authorization} header</li>
 *   <li>Wrapping service results in {@link com.justrocketscience.common.ApiResponse}</li>
 *   <li>OpenAPI documentation via Springdoc annotations</li>
 * </ul>
 *
 * <p>This class contains <strong>zero business logic</strong>. Every decision about
 * token validity, password correctness, uniqueness, or session state lives in
 * {@link AuthService}. The controller is a thin translation layer between HTTP
 * and the service.
 *
 * <p>Exception mapping is handled entirely by {@link AuthExceptionHandler}
 * ({@code @RestControllerAdvice}). The controller never catches exceptions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(
    name = "Authentication",
    description = "Registration, login, token refresh, logout, profile, and password management"
)
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/register
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new user account.
     *
     * <p><b>Request flow:</b>
     * <ol>
     *   <li>Jackson deserializes JSON body into {@link RegisterRequest}</li>
     *   <li>{@code @Valid} triggers Jakarta Validation — field constraints on
     *       username, email, password (size, pattern, complexity). Violation → 400
     *       handled by {@code AuthExceptionHandler.handleMethodArgumentNotValid}.</li>
     *   <li>{@code AuthService.register()} checks uniqueness, encodes password, persists user.</li>
     *   <li>Returns 201 with a success message. No tokens.</li>
     * </ol>
     *
     * <p><b>Response flow:</b>
     * 201 Created — body is {@code ApiResponse<RegisterResponse>} with a human-readable message.
     * No {@code Location} header is set — the "resource" created is a session obtained via login,
     * not a directly addressable user URL. Exposing {@code /users/{id}} at registration time
     * would leak the userId before the client has authenticated.
     *
     * <p><b>Status code decision — 201 not 200:</b>
     * A new persistent resource (the user account) was created. RFC 7231 §6.3.2 is unambiguous
     * here — side-effect-producing POST that creates a resource returns 201.
     *
     * <p><b>Security considerations:</b>
     * <ul>
     *   <li>No tokens are returned. Forces an explicit login, which allows future phases
     *       (email verification, MFA) to gate token issuance without changing this endpoint.</li>
     *   <li>Error messages never confirm which field (email or username) conflicted — prevents
     *       user enumeration via the registration endpoint.</li>
     *   <li>Password complexity is validated here (min 8, max 100, complexity pattern) — the
     *       max 100 cap prevents BCrypt DoS (BCrypt silently truncates at 72 bytes, but
     *       pre-hashing a 100MB password string is still expensive).</li>
     * </ul>
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user account",
        description = "Creates a new user. Does not return tokens — call /login after registration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created successfully",
            content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failure — see errors array",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Email or username already exists",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<com.justrocketscience.common.ApiResponse<RegisterResponse>> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse result = authService.register(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(com.justrocketscience.common.ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and returns an access + refresh token pair.
     *
     * <p><b>Request flow:</b>
     * <ol>
     *   <li>Jackson deserializes JSON body into {@link LoginRequest}</li>
     *   <li>{@code @Valid} checks {@code @NotBlank} and {@code @Size(max=100)} on password.
     *       No complexity pattern check at login — see Step 5 DTO decisions.</li>
     *   <li>{@code AuthService.login()} delegates to {@code AuthenticationManager},
     *       generates tokens, persists hashed refresh token.</li>
     *   <li>Returns 200 with the token pair.</li>
     * </ol>
     *
     * <p><b>Response flow:</b>
     * 200 OK — body is {@code ApiResponse<AuthResponse>} containing:
     * {@code accessToken}, {@code refreshToken}, {@code tokenType="Bearer"},
     * {@code expiresIn} (seconds), {@code expiresAt} (ISO-8601 Instant).
     *
     * <p>The response also sets {@code Cache-Control: no-store} to prevent tokens from being
     * cached by proxies or the browser's HTTP cache. This is required by RFC 6749 §5.1.
     *
     * <p><b>Status code decision — 200 not 201:</b>
     * Login creates a session/token, not a durable REST resource. No resource URI exists for a
     * token. 200 is correct for a successful authentication that returns data. Some APIs use 201
     * here — that is arguable but incorrect per RFC 7231 (201 requires a Location header pointing
     * to the created resource).
     *
     * <p><b>Security considerations:</b>
     * <ul>
     *   <li>Bad credentials and disabled accounts both return 401. The response body message
     *       must not distinguish between them — "Invalid email or password" for all failures.</li>
     *   <li>The {@code deviceInfo} field (User-Agent) is captured from the request header
     *       for the "active sessions" display feature. It is nullable — some clients don't send it.</li>
     *   <li>Rate limiting is enforced at the Gateway layer (Redis token bucket on
     *       {@code POST /login}) — not here. The controller trusts that the Gateway has
     *       already applied it.</li>
     * </ul>
     */
    @PostMapping("/login")
    @Operation(
        summary = "Authenticate and receive a token pair",
        description = "Returns a short-lived access token (15 min) and a long-lived refresh token (7 days)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authentication successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Malformed request body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or account disabled",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<com.justrocketscience.common.ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        // Capture User-Agent for device tracking — nullable, some clients omit it
        String deviceInfo = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        LoginRequest enriched = new LoginRequest(request.email(), request.password(), deviceInfo);

        AuthResponse result = authService.login(enriched);

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("Pragma", "no-cache")
            .body(com.justrocketscience.common.ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/refresh
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rotates a refresh token and returns a new access + refresh token pair.
     *
     * <p><b>Request flow:</b>
     * <ol>
     *   <li>Body contains only {@code refreshToken} — no Authorization header needed
     *       (the access token may be expired, which is the typical reason for calling refresh).</li>
     *   <li>{@code @Valid} checks {@code @NotBlank} on the refresh token field.</li>
     *   <li>{@code AuthService.refresh()} validates JWT, checks DB for revocation status,
     *       atomically revokes old and issues new tokens.</li>
     * </ol>
     *
     * <p><b>Response flow:</b>
     * 200 OK — same shape as login response: {@code ApiResponse<AuthResponse>}.
     * Includes {@code Cache-Control: no-store}.
     *
     * <p><b>Status code decision — 200:</b>
     * Refresh returns a replacement token pair. No new resource is created — the existing
     * session continues. 200 is correct.
     *
     * <p><b>Security considerations:</b>
     * <ul>
     *   <li>The refresh endpoint is NOT guarded by {@code JwtAuthenticationFilter}. The access
     *       token is expired at this point — that is the purpose of calling refresh. Requiring a
     *       valid access token to refresh would be circular.</li>
     *   <li>Reuse detection (revoked token presented) returns 401 with a generic message. The
     *       full session revocation happens in the service layer; the client is not told why.</li>
     *   <li>This endpoint is not rate-limited at the controller. The Gateway applies the
     *       token-bucket rate limiter to all {@code /api/v1/auth/**} paths.</li>
     * </ul>
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "Rotate a refresh token",
        description = "Validates the refresh token, revokes it, and issues a new access + refresh pair. " +
                      "Presents the old refresh token; receives a completely new pair. " +
                      "Do NOT send an Authorization header — the access token may be expired."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token pair rotated successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or blank refresh token",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Invalid, expired, or already-revoked refresh token",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<com.justrocketscience.common.ApiResponse<AuthResponse>> refresh(
        @Valid @RequestBody RefreshRequest request
    ) {
        AuthResponse result = authService.refresh(request);

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("Pragma", "no-cache")
            .body(com.justrocketscience.common.ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/logout
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs out the current user from the current device.
     *
     * <p><b>Request flow:</b>
     * <ol>
     *   <li>Access token is extracted from the {@code Authorization: Bearer <token>} header.
     *       The raw token string (without the "Bearer " prefix) is passed to the service.</li>
     *   <li>Request body optionally contains {@code refreshToken} for single-device revocation.
     *       If absent, only the access token JTI is blacklisted.</li>
     *   <li>{@code AuthService.logout()} blacklists the JTI in Redis and revokes the refresh
     *       token row in the DB.</li>
     * </ol>
     *
     * <p><b>Response flow:</b>
     * 204 No Content — no body. The client should discard both tokens.
     *
     * <p><b>Status code decision — 204 not 200:</b>
     * Logout produces no meaningful response body. 204 communicates "success, nothing to return"
     * more precisely than 200 with an empty or placeholder body. Some APIs return 200 with a
     * message — this is acceptable but adds noise. 204 is the idiomatic REST choice for
     * destructive operations that leave nothing to describe.
     *
     * <p><b>Security considerations:</b>
     * <ul>
     *   <li>This endpoint requires a valid Bearer token ({@code SecurityConfig} guards it).
     *       The {@code JwtAuthenticationFilter} has already validated the token before this
     *       method is called.</li>
     *   <li>Logout is always idempotent — calling it twice with the same token is safe.
     *       The second call returns 204 even if the token is already blacklisted.</li>
     *   <li>The Authorization header is extracted here (not injected) because the raw token
     *       string is needed for JTI extraction. {@code @AuthenticationPrincipal} gives the
     *       principal but not the raw token. The raw token is never logged.</li>
     *   <li>Logout-all-devices is a separate endpoint to keep the semantics explicit.
     *       A client that calls {@code POST /logout} with no body knows it is doing
     *       single-device logout.</li>
     * </ul>
     */
    @PostMapping("/logout")
    @Operation(
        summary = "Logout from the current device",
        description = "Blacklists the access token JTI in Redis and revokes the refresh token. " +
                      "Supply the refresh token in the request body for full single-device cleanup. " +
                      "Omitting it blacklists the access token only.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> logout(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,

        @RequestBody(required = false) LogoutRequest request
    ) {
        String rawAccessToken  = extractBearerToken(authorizationHeader);
        String rawRefreshToken = (request != null) ? request.refreshToken() : null;

        authService.logout(rawAccessToken, rawRefreshToken);

        return ResponseEntity.noContent().build();
    }

    /**
     * Logs out the current user from ALL devices.
     *
     * <p>Revokes all refresh token rows for the user and blacklists the current access token.
     * Use this after a password change, suspected compromise, or "sign out everywhere" UI action.
     *
     * <p><b>Status code decision — 204:</b> Same rationale as single-device logout.
     */
    @PostMapping("/logout/all")
    @Operation(
        summary = "Logout from all devices",
        description = "Revokes all active refresh tokens for the current user and blacklists " +
                      "the current access token. All other devices will be signed out on " +
                      "their next token use.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "All sessions revoked"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> logoutAllDevices(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        String rawAccessToken = extractBearerToken(authorizationHeader);
        authService.logoutAllDevices(rawAccessToken);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/auth/me
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     *
     * <p><b>Request flow:</b>
     * <ol>
     *   <li>No request body. No path variables. No query parameters.</li>
     *   <li>{@code JwtAuthenticationFilter} has already populated the SecurityContext
     *       before this method is invoked. The principal is injected via
     *       {@code @AuthenticationPrincipal} — no manual SecurityContext access in
     *       the controller.</li>
     *   <li>{@code AuthService.getCurrentUser()} reads the full profile from the DB
     *       (one SELECT by userId) and maps to {@link UserProfileResponse}.</li>
     * </ol>
     *
     * <p><b>Response flow:</b>
     * 200 OK — body is {@code ApiResponse<UserProfileResponse>}:
     * {@code userId}, {@code username}, {@code email}, {@code role}, {@code isActive},
     * {@code createdAt}.
     *
     * <p>The response includes {@code ETag} and {@code Cache-Control: private, max-age=60}
     * headers. Profile data is user-specific (private) and changes infrequently.
     * A 60-second client cache reduces redundant calls from SPAs that render the profile
     * on multiple pages. The ETag enables conditional GET ({@code If-None-Match}) so the
     * client can validate the cache without receiving the full body.
     *
     * <p><b>Status code decision — 200:</b>
     * Standard GET — resource exists and is returned. 200.
     *
     * <p><b>Security considerations:</b>
     * <ul>
     *   <li>This endpoint is guarded by {@code SecurityConfig} — a valid Bearer token is required.</li>
     *   <li>The {@code @AuthenticationPrincipal} annotation is preferred over
     *       {@code SecurityContextHolder.getContext().getAuthentication()} in the controller.
     *       It is more testable (injected in MockMvc) and communicates intent clearly.</li>
     *   <li>The response never includes {@code passwordHash}, {@code updatedAt},
     *       or any refresh token data — enforced by the {@code UserProfileResponse} record
     *       shape and the {@code UserMapper} compile-time check.</li>
     *   <li>{@code Cache-Control: private} prevents shared caches (CDNs, proxies) from
     *       storing profile data. Only the end client may cache it.</li>
     * </ul>
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current user profile",
        description = "Returns the profile of the authenticated user. Requires a valid Bearer token.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile returned",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "User account not found (JWT references deleted account)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<com.justrocketscience.common.ApiResponse<UserProfileResponse>> me() {
        UserProfileResponse profile = authService.getCurrentUser();

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
            .body(com.justrocketscience.common.ApiResponse.success(profile));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/auth/change-password
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Changes the authenticated user's password.
     *
     * <p><b>Request flow:</b>
     * <ol>
     *   <li>Jackson deserializes the body into {@link ChangePasswordRequest}.</li>
     *   <li>{@code @Valid} triggers field-level and class-level constraints:
     *       {@code @NotBlank} on both fields; {@code @Size(8–100)} and {@code @Pattern}
     *       on {@code newPassword}; {@code @PasswordsNotEqual} (class-level) verifies
     *       the two fields differ.</li>
     *   <li>The raw access token is extracted from the Authorization header to be
     *       blacklisted after the password change.</li>
     *   <li>{@code AuthService.changePassword()} verifies current password via BCrypt,
     *       encodes and stores the new hash, revokes all refresh tokens, and blacklists
     *       the current access token.</li>
     * </ol>
     *
     * <p><b>Response flow:</b>
     * 204 No Content. The client must discard all tokens and re-login.
     *
     * <p><b>Status code decision — 204 not 200:</b>
     * A successful password change has no meaningful response body. The action is complete;
     * the client's next step is to re-login. 204 is correct.
     *
     * <p><b>HTTP method decision — PUT not PATCH:</b>
     * {@code PATCH} implies partial update of a resource's representation. The password change
     * operation is not a partial update — it requires verifying the current password, which is
     * a domain operation, not a PATCH over a user resource. {@code PUT} to a dedicated
     * sub-resource path ({@code /change-password}) is a common pattern for actions that look
     * like mutations but have complex domain rules. {@code POST} would also be acceptable.
     *
     * <p><b>Security considerations:</b>
     * <ul>
     *   <li>Requires a valid Bearer token — secured by {@code SecurityConfig}.</li>
     *   <li>Requires the current password in the body even with a valid JWT — the controller
     *       passes the full request to the service without pre-validating the current password.
     *       This is intentional: the controller has no access to the stored hash.</li>
     *   <li>After success, the client's access token is blacklisted and all refresh tokens are
     *       revoked. The client MUST discard all stored tokens and re-authenticate. This is
     *       communicated via the {@code X-Token-Revoked: true} response header, which the
     *       frontend SDK can intercept to trigger a redirect to the login screen.</li>
     *   <li>The raw access token is extracted here (same pattern as logout) for post-change
     *       blacklisting.</li>
     * </ul>
     */
    @PutMapping("/change-password")
    @Operation(
        summary = "Change the current user's password",
        description = "Requires the current password for verification. On success, all sessions " +
                      "are revoked and the client must re-authenticate. " +
                      "The response header X-Token-Revoked: true signals the client to clear stored tokens.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed. All sessions revoked.",
            headers = @Header(name = "X-Token-Revoked", description = "Always 'true' on success",
                schema = @Schema(type = "string", example = "true"))),
        @ApiResponse(responseCode = "400", description = "Validation failure or new password same as old",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Missing/invalid token or wrong current password",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,

        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        String rawAccessToken = extractBearerToken(authorizationHeader);
        authService.changePassword(request, rawAccessToken);

        return ResponseEntity.noContent()
            .header("X-Token-Revoked", "true")
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Strips the {@code "Bearer "} prefix from an Authorization header value.
     *
     * <p>The filter chain ({@code JwtAuthenticationFilter}) has already validated this
     * header format before this controller method is reached, so an invalid format here
     * would be a misconfigured {@code permitAll()} route. The explicit check is a
     * defensive guard for that scenario.
     *
     * @param authorizationHeader the full header value (e.g. {@code "Bearer eyJhb..."})
     * @return the raw JWT string
     * @throws MalformedAuthHeaderException if the header is missing or not "Bearer " prefixed
     */
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new MalformedAuthHeaderException(
                "Authorization header must be in the format: Bearer <token>"
            );
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
