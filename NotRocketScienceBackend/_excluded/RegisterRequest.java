package com.justrocketscience.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for POST /api/v1/auth/register.
 *
 * <p>Implemented as a Java record — immutable by construction, no boilerplate,
 * and Jackson deserializes records correctly with Spring Boot 3 out of the box.
 * Records are the correct choice for request DTOs: they are never mutated after
 * deserialization, they provide structural equality, and they prevent accidental
 * field modification downstream in the service layer.
 *
 * <h3>Security notes</h3>
 * <ul>
 *   <li>Password validation is intentionally minimal here — complexity rules
 *       (uppercase, digit, special char) are enforced by the @Pattern below.
 *       The raw password never appears in logs; the service BCrypts it
 *       before any persistence call.</li>
 *   <li>The 100-character upper bound on password prevents BCrypt DoS attacks.
 *       BCrypt cost scales with input length; unrestricted input allows an
 *       attacker to send a 1MB password and monopolise a CPU core.</li>
 *   <li>Username pattern disallows spaces and special chars to prevent
 *       injection into display contexts (HTML, logs) without relying solely
 *       on output encoding.</li>
 * </ul>
 *
 * <h3>Validation alignment with DB schema (V1__create_users.sql)</h3>
 * <pre>
 *   username VARCHAR(50)  → @Size(max = 50)
 *   email    VARCHAR(150) → @Size(max = 150)
 *   password VARCHAR(255) not stored; BCrypt output is always ≤ 72 chars input
 * </pre>
 */
public record RegisterRequest(

        /**
         * Chosen display name and login handle.
         *
         * @NotBlank — rejects null, empty string, and whitespace-only strings.
         *   A plain @NotNull would accept "   " as a valid username.
         * @Size(min=3) — single-char usernames are effectively unguessable as
         *   identifiers but terrible UX and enumerable via brute force.
         * @Size(max=50) — mirrors VARCHAR(50) in DB; prevents truncation surprises.
         * @Pattern — alphanumeric + underscore + hyphen only. Blocks injection
         *   characters (<, >, ', ", &) at the source. Anchored with ^ and $.
         */
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message = "Username may only contain letters, numbers, underscores, and hyphens"
        )
        String username,

        /**
         * User's email address — doubles as the login identifier.
         *
         * @Email — Jakarta's built-in RFC 5322 format check. Not a strict RFC
         *   validator (those reject real-world addresses), but catches obvious
         *   malformat like "notanemail".
         * @Size(max=150) — mirrors VARCHAR(150) in DB.
         * No @NotBlank needed — @Email implicitly rejects blank strings.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        /**
         * Raw password — exists only in this DTO. Never stored, never logged.
         * The service layer BCrypts this before any DB write.
         *
         * @Size(min=8) — NIST SP 800-63B minimum. Below 8 chars the entropy
         *   is too low for offline brute-force resistance even with BCrypt.
         * @Size(max=100) — BCrypt DoS prevention. BCrypt ignores input beyond
         *   72 bytes; capping at 100 chars makes the limit explicit and prevents
         *   attackers submitting megabyte strings through the endpoint.
         * @Pattern — requires at least one uppercase, one lowercase, one digit,
         *   and one special character. Rejects dictionary words with no complexity.
         *   The message lists the exact requirements so the client can display
         *   a useful error without parsing the pattern.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100,
              message = "Password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, " +
                          "one digit, and one special character (@$!%*?&_-#)"
        )
        String password

) {}
