package com.justrocketscience.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Outbound DTO returned from POST /auth/login and POST /auth/refresh.
 *
 * <p>Implemented as a record — response DTOs are built once by the service
 * and serialized immediately; they are never modified after construction.
 * Jackson serializes records correctly with Spring Boot 3 without any
 * additional configuration.
 *
 * <h3>Token transport strategy</h3>
 * Both tokens are returned in the response body (not Set-Cookie headers).
 * The gateway and frontend handle storage. HttpOnly cookie transport is a
 * valid alternative with stronger XSS protection, but requires CORS
 * credentials and SameSite configuration that adds complexity to the gateway
 * layer. This can be changed later via {@link com.justrocketscience.auth.util.CookieUtil}
 * without altering this DTO.
 *
 * <h3>Security notes</h3>
 * <ul>
 *   <li>{@code accessToken} — short-lived (15 min). Used as Bearer in
 *       Authorization header for all downstream service calls.</li>
 *   <li>{@code refreshToken} — long-lived (7 days). Used ONLY to rotate tokens
 *       via POST /auth/refresh. Should never be sent to any other endpoint.</li>
 *   <li>{@code expiresAt} — client uses this to proactively refresh before
 *       expiry rather than waiting for a 401 response.</li>
 *   <li>No user data is included here — avoids coupling the auth response
 *       shape to the user profile shape. If login + profile is needed,
 *       the client makes a follow-up GET /auth/me.</li>
 * </ul>
 *
 * <h3>Serialization notes</h3>
 * {@code @JsonProperty} on record components is supported from Jackson 2.14+
 * (bundled with Spring Boot 3). Used here to control the exact JSON key names
 * regardless of future record component renaming.
 */
public record AuthResponse(

        /**
         * Short-lived JWT access token (15 min).
         * Sent as {@code Authorization: Bearer <token>} on every API request.
         * Contains claims: sub (userId), role, jti, iat, exp.
         */
        @JsonProperty("access_token")
        String accessToken,

        /**
         * Long-lived opaque refresh token (7 days).
         * Used exclusively with POST /auth/refresh.
         * The raw token is stored hashed in the DB — this is the pre-hash value
         * that the client must present to rotate the token pair.
         */
        @JsonProperty("refresh_token")
        String refreshToken,

        /**
         * Always "Bearer" — the token scheme as defined in RFC 6750.
         * Included so clients don't need to hardcode the scheme and can
         * construct the Authorization header dynamically:
         * {@code tokenType + " " + accessToken}.
         */
        @JsonProperty("token_type")
        String tokenType,

        /**
         * Access token lifetime in seconds (e.g. 900 for 15 minutes).
         * Clients use this to schedule a refresh call before the token expires,
         * preventing mid-request 401s during active usage.
         *
         * <p>Named {@code expires_in} following RFC 6749 (OAuth 2.0) convention
         * so the response is compatible with OAuth-aware clients and libraries.
         */
        @JsonProperty("expires_in")
        long expiresIn,

        /**
         * Absolute UTC instant when the access token expires.
         * Complement to {@code expires_in} — some clients prefer an absolute
         * timestamp over a relative seconds value, especially when clock drift
         * between client and server is a concern.
         * Serialized as ISO-8601 string by Jackson's JavaTimeModule.
         */
        @JsonProperty("expires_at")
        Instant expiresAt

) {
    /**
     * Factory method — constructs an AuthResponse with "Bearer" type pre-set.
     * The service calls this rather than the canonical constructor to avoid
     * repeating the "Bearer" literal at every call site.
     */
    public static AuthResponse of(String accessToken,
                                   String refreshToken,
                                   long expiresInSeconds,
                                   Instant expiresAt) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, expiresAt);
    }
}
