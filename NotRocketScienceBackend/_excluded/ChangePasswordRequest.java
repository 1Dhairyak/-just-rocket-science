package com.justrocketscience.auth.dto.request;

import com.justrocketscience.auth.dto.request.validation.PasswordsNotEqual;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for PUT /api/v1/auth/change-password.
 * Requires a valid Bearer token — the endpoint is authenticated.
 *
 * <h3>Design: why include currentPassword</h3>
 * Even though the endpoint requires a valid JWT, we still ask for
 * {@code currentPassword}. This defends against CSRF scenarios and
 * stolen-but-unexpired access tokens: an attacker with a token can't
 * silently change the password without also knowing the current one.
 * This is the same pattern used by GitHub, Google, and AWS IAM.
 *
 * <h3>Cross-field validation</h3>
 * {@code @PasswordsNotEqual} is a custom constraint (defined below this class)
 * that verifies {@code currentPassword != newPassword} at the class level.
 * Field-level constraints run first; the cross-field check only runs if both
 * fields individually pass. This avoids confusing "must not be equal" errors
 * when one field is blank.
 *
 * <h3>Security notes</h3>
 * <ul>
 *   <li>After a successful password change, the service calls
 *       {@code revokeAllByUserId} to invalidate all existing refresh tokens.
 *       All other sessions are forced to re-authenticate.</li>
 *   <li>currentPassword is verified via BCrypt.matches() — not stored anywhere.</li>
 *   <li>The same BCrypt DoS cap (max=100) applies to both fields.</li>
 * </ul>
 */
@PasswordsNotEqual
public record ChangePasswordRequest(

        /**
         * The user's existing password — verified by BCrypt before the change proceeds.
         * Prevents CSRF and stolen-token attacks from silently locking out the real user.
         *
         * @NotBlank — blank current password would waste a BCrypt round-trip
         *   before failing. Reject it structurally instead.
         * @Size(max=100) — BCrypt DoS cap.
         */
        @NotBlank(message = "Current password is required")
        @Size(max = 100, message = "Current password must not exceed 100 characters")
        String currentPassword,

        /**
         * The desired new password.
         * Same complexity rules as RegisterRequest — enforces the policy
         * consistently across registration and password change flows.
         *
         * @NotBlank + @Size(min=8) + @Pattern — full policy applied.
         * A password that was valid at registration must remain valid on change.
         */
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100,
              message = "New password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]+$",
                message = "New password must contain at least one uppercase letter, " +
                          "one lowercase letter, one digit, and one special character (@$!%*?&_-#)"
        )
        String newPassword

) {}
