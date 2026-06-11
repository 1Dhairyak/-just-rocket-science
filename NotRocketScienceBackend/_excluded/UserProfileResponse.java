package com.justrocketscience.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.justrocketscience.auth.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound DTO returned from GET /api/v1/auth/me.
 * Represents the public-facing profile of the authenticated user.
 *
 * <p>This is a strict projection of the {@link com.justrocketscience.auth.entity.User}
 * entity — it exposes only the fields that are safe and meaningful for the client.
 * Fields like {@code passwordHash}, internal flags, and audit metadata are
 * deliberately absent.
 *
 * <h3>What is NOT included and why</h3>
 * <ul>
 *   <li>{@code passwordHash} — never serialized; BCrypt output is useless to the
 *       client and leaking it in a response body would be a serious security incident.</li>
 *   <li>{@code updatedAt} — internal audit field; not meaningful to the end user
 *       in a profile context. Available if needed via an admin endpoint.</li>
 *   <li>Refresh token data — belongs to the auth flow, not the profile.</li>
 * </ul>
 *
 * <h3>Why include isActive</h3>
 * {@code isActive} is included because the client may need to render
 * an "account suspended" notice. The authenticated user with a valid token
 * can still hit /me even if their account was deactivated after their last login.
 * The service checks this and returns 403 if inactive, but the field
 * is present for admin contexts where the response is used differently.
 *
 * <h3>Serialization notes</h3>
 * <ul>
 *   <li>{@code id} serialized as UUID string — safe for JSON; no integer
 *       enumeration risk.</li>
 *   <li>{@code role} serialized as the enum name ("USER", "ADMIN") because
 *       {@code EnumType.STRING} is used on the entity. Consistent across
 *       the entire stack.</li>
 *   <li>{@code createdAt} serialized as ISO-8601 UTC string via Jackson's
 *       JavaTimeModule. Ensure {@code spring.jackson.serialization.write-dates-as-timestamps=false}
 *       is set in application.yml — otherwise it serializes as epoch milliseconds.</li>
 * </ul>
 *
 * <h3>Downstream use</h3>
 * Other services (Agency, Rocket) receive {@code X-User-Id} and {@code X-User-Role}
 * headers from the Gateway — they don't call /me. This DTO is only for
 * the frontend to display the user's own profile.
 */
public record UserProfileResponse(

        /**
         * The user's UUID primary key.
         * Safe to expose — UUIDs are non-enumerable and non-guessable.
         * Required by the frontend to reference the user in subsequent API calls.
         */
        UUID id,

        /**
         * The user's chosen display name.
         * Used in UI headers, comments, and attribution.
         */
        String username,

        /**
         * The user's email address.
         * Needed for "account settings" display and for the user to verify
         * which email is registered.
         */
        String email,

        /**
         * The user's authorization role.
         * Serialized as "USER" or "ADMIN" (enum name, not ordinal).
         * The frontend uses this to show/hide admin-only UI elements.
         * Returned as the UserRole enum — Jackson serializes it as its name string.
         */
        UserRole role,

        /**
         * Whether the account is currently active.
         * Allows the frontend to display an account status notice.
         *
         * @JsonProperty maps the Java boolean field name to a snake_case JSON key,
         *   consistent with the rest of the API's naming convention.
         */
        @JsonProperty("is_active")
        Boolean isActive,

        /**
         * UTC instant when the account was created.
         * Displayed as "Member since" in UI profiles.
         * Serialized as ISO-8601 string ("2024-01-15T10:30:00Z").
         */
        @JsonProperty("created_at")
        Instant createdAt

) {}
