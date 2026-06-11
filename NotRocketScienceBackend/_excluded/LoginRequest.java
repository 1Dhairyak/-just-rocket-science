package com.justrocketscience.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for POST /api/v1/auth/login.
 *
 * <p>Deliberately minimal — only the two fields needed to authenticate.
 * No username field: the system uses email as the sole login identifier,
 * which prevents the username-enumeration attack where an attacker discovers
 * valid usernames by probing the login endpoint with different values.
 *
 * <h3>Security notes</h3>
 * <ul>
 *   <li>Validation messages on login are intentionally generic — "Invalid credentials"
 *       is returned by the service for both wrong email and wrong password.
 *       The field-level messages here are only for structural issues (blank, too long),
 *       not for business-logic failures.</li>
 *   <li>Password max length mirrors RegisterRequest — same BCrypt DoS prevention.
 *       An attacker who skips registration can still POST to /login with a huge body.</li>
 *   <li>No @Pattern on password here — we don't want to leak that a stored password
 *       violates the current complexity policy (e.g., old accounts registered before
 *       the policy was introduced). Pattern rejection at login would differ from
 *       "wrong password" and act as an oracle.</li>
 * </ul>
 *
 * <h3>Why a record</h3>
 * Same reasoning as RegisterRequest — login credentials are deserialized once
 * and immediately consumed by AuthService. Immutability prevents accidental
 * mutation between the controller and service layer.
 */
public record LoginRequest(

        /**
         * Email used as the primary login identifier.
         *
         * @NotBlank — prevents empty-string submissions that would hit
         *   the DB with a WHERE email = '' query.
         * @Email — basic format check so malformed strings don't reach
         *   the repository layer. Not a business-logic check — wrong-but-valid
         *   emails return 401, not 400.
         * @Size(max=150) — mirrors DB column; prevents excessively long
         *   strings from reaching the query layer.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        /**
         * Raw password submitted by the client.
         * BCrypt.matches() is called by the service — this field is never
         * stored or logged anywhere.
         *
         * @NotBlank — a blank password would pass through to BCrypt
         *   and waste CPU time comparing against a stored hash.
         * @Size(max=100) — BCrypt DoS prevention (same as RegisterRequest).
         *   No minimum length: we accept whatever the user typed; a wrong
         *   short password is still a wrong password, not a validation error.
         */
        @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password must not exceed 100 characters")
        String password

) {}
