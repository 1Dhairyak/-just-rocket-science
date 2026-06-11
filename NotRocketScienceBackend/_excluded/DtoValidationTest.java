package com.justrocketscience.auth.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for DTO validation constraints.
 * No Spring context — uses the Hibernate Validator implementation directly.
 * Tests run in under 100ms total.
 *
 * <h3>Testing strategy</h3>
 * For each DTO, tests cover:
 * <ul>
 *   <li>Valid input produces zero violations</li>
 *   <li>Each constraint produces exactly the right violation on the right field</li>
 *   <li>Boundary values (min length, max length, edge-case formats)</li>
 *   <li>Security-sensitive cases (BCrypt DoS, password complexity)</li>
 *   <li>Cross-field constraints (ChangePasswordRequest)</li>
 * </ul>
 *
 * <h3>Helper pattern</h3>
 * {@code violationsFor()} and {@code fieldViolations()} are private helpers
 * that keep each test focused on assertion rather than setup.
 */
class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("RegisterRequest")
    class RegisterRequestValidation {

        private final String VALID_USERNAME = "john_doe";
        private final String VALID_EMAIL    = "john@example.com";
        private final String VALID_PASSWORD = "Password1!";

        @Test
        @DisplayName("valid request produces no violations")
        void validRequest_noViolations() {
            var request = new RegisterRequest(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
            assertThat(violationsFor(request)).isEmpty();
        }

        // -- username --

        @ParameterizedTest(name = "username=''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("username — blank or null fails @NotBlank")
        void username_blank_fails(String username) {
            var request = new RegisterRequest(username, VALID_EMAIL, VALID_PASSWORD);
            assertThat(fieldViolations(request, "username")).isNotEmpty();
        }

        @Test
        @DisplayName("username — too short (< 3 chars) fails @Size")
        void username_tooShort_fails() {
            var request = new RegisterRequest("ab", VALID_EMAIL, VALID_PASSWORD);
            assertThat(fieldViolations(request, "username")).isNotEmpty();
        }

        @Test
        @DisplayName("username — exactly 3 chars is valid (boundary)")
        void username_minLength_valid() {
            var request = new RegisterRequest("abc", VALID_EMAIL, VALID_PASSWORD);
            assertThat(violationsFor(request)).isEmpty();
        }

        @Test
        @DisplayName("username — 50 chars is valid (upper boundary)")
        void username_maxLength_valid() {
            String maxUsername = "a".repeat(50);
            var request = new RegisterRequest(maxUsername, VALID_EMAIL, VALID_PASSWORD);
            assertThat(violationsFor(request)).isEmpty();
        }

        @Test
        @DisplayName("username — 51 chars fails @Size")
        void username_tooLong_fails() {
            String longUsername = "a".repeat(51);
            var request = new RegisterRequest(longUsername, VALID_EMAIL, VALID_PASSWORD);
            assertThat(fieldViolations(request, "username")).isNotEmpty();
        }

        @ParameterizedTest(name = "username=''{0}''")
        @ValueSource(strings = {"user name", "user@name", "user<script>", "user/admin"})
        @DisplayName("username — special chars fail @Pattern")
        void username_specialChars_fail(String username) {
            var request = new RegisterRequest(username, VALID_EMAIL, VALID_PASSWORD);
            assertThat(fieldViolations(request, "username")).isNotEmpty();
        }

        @ParameterizedTest(name = "username=''{0}''")
        @ValueSource(strings = {"user_name", "user-name", "UserName123", "abc"})
        @DisplayName("username — valid patterns accepted by @Pattern")
        void username_validPatterns_accepted(String username) {
            var request = new RegisterRequest(username, VALID_EMAIL, VALID_PASSWORD);
            assertThat(violationsFor(request)).isEmpty();
        }

        // -- email --

        @ParameterizedTest(name = "email=''{0}''")
        @ValueSource(strings = {"notanemail", "missing@tld", "@nodomain.com", "no-at-sign"})
        @DisplayName("email — malformed fails @Email")
        void email_malformed_fails(String email) {
            var request = new RegisterRequest(VALID_USERNAME, email, VALID_PASSWORD);
            assertThat(fieldViolations(request, "email")).isNotEmpty();
        }

        @Test
        @DisplayName("email — 151 chars fails @Size")
        void email_tooLong_fails() {
            String longEmail = "a".repeat(141) + "@example.com"; // > 150
            var request = new RegisterRequest(VALID_USERNAME, longEmail, VALID_PASSWORD);
            assertThat(fieldViolations(request, "email")).isNotEmpty();
        }

        // -- password --

        @Test
        @DisplayName("password — 7 chars fails @Size min=8")
        void password_tooShort_fails() {
            var request = new RegisterRequest(VALID_USERNAME, VALID_EMAIL, "Pass1!x");
            assertThat(fieldViolations(request, "password")).isNotEmpty();
        }

        @Test
        @DisplayName("password — 8 chars with all complexity is valid (boundary)")
        void password_minLength_valid() {
            var request = new RegisterRequest(VALID_USERNAME, VALID_EMAIL, "Pass1!ab");
            assertThat(violationsFor(request)).isEmpty();
        }

        @Test
        @DisplayName("SECURITY: password — 101 chars fails @Size max=100 (BCrypt DoS prevention)")
        void password_over100Chars_fails() {
            // BCrypt DoS: reject huge passwords before they reach the encoder
            String longPassword = "Password1!" + "x".repeat(91); // 101 chars
            var request = new RegisterRequest(VALID_USERNAME, VALID_EMAIL, longPassword);
            assertThat(fieldViolations(request, "password")).isNotEmpty();
        }

        @ParameterizedTest(name = "password=''{0}''")
        @ValueSource(strings = {
                "alllowercase1!",    // no uppercase
                "ALLUPPERCASE1!",    // no lowercase
                "NoDigitsHere!",     // no digit
                "NoSpecialChar1",    // no special char
        })
        @DisplayName("password — complexity violations fail @Pattern")
        void password_missingComplexity_fails(String password) {
            var request = new RegisterRequest(VALID_USERNAME, VALID_EMAIL, password);
            assertThat(fieldViolations(request, "password")).isNotEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("LoginRequest")
    class LoginRequestValidation {

        @Test
        @DisplayName("valid request produces no violations")
        void validRequest_noViolations() {
            var request = new LoginRequest("john@example.com", "anypassword");
            assertThat(violationsFor(request)).isEmpty();
        }

        @Test
        @DisplayName("blank email fails")
        void blankEmail_fails() {
            var request = new LoginRequest("", "password");
            assertThat(fieldViolations(request, "email")).isNotEmpty();
        }

        @Test
        @DisplayName("malformed email fails @Email")
        void malformedEmail_fails() {
            var request = new LoginRequest("notanemail", "password");
            assertThat(fieldViolations(request, "email")).isNotEmpty();
        }

        @Test
        @DisplayName("blank password fails @NotBlank")
        void blankPassword_fails() {
            var request = new LoginRequest("john@example.com", "");
            assertThat(fieldViolations(request, "password")).isNotEmpty();
        }

        @Test
        @DisplayName("SECURITY: password over 100 chars fails (BCrypt DoS prevention)")
        void password_over100Chars_fails() {
            String longPassword = "x".repeat(101);
            var request = new LoginRequest("john@example.com", longPassword);
            assertThat(fieldViolations(request, "password")).isNotEmpty();
        }

        @Test
        @DisplayName("short password (e.g. 3 chars) is accepted at login — no min length")
        void shortPassword_isAccepted_atLogin() {
            // Login doesn't enforce complexity — that would leak policy info
            var request = new LoginRequest("john@example.com", "abc");
            assertThat(violationsFor(request)).isEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("ChangePasswordRequest")
    class ChangePasswordRequestValidation {

        @Test
        @DisplayName("valid request with different passwords produces no violations")
        void validRequest_noViolations() {
            var request = new ChangePasswordRequest("OldPassword1!", "NewPassword2@");
            assertThat(violationsFor(request)).isEmpty();
        }

        @Test
        @DisplayName("blank currentPassword fails @NotBlank")
        void blankCurrentPassword_fails() {
            var request = new ChangePasswordRequest("", "NewPassword2@");
            assertThat(fieldViolations(request, "currentPassword")).isNotEmpty();
        }

        @Test
        @DisplayName("newPassword too short fails @Size")
        void newPassword_tooShort_fails() {
            var request = new ChangePasswordRequest("OldPassword1!", "Short1!");
            assertThat(fieldViolations(request, "newPassword")).isNotEmpty();
        }

        @Test
        @DisplayName("newPassword without complexity fails @Pattern")
        void newPassword_noComplexity_fails() {
            var request = new ChangePasswordRequest("OldPassword1!", "alllowercase1!");
            assertThat(fieldViolations(request, "newPassword")).isNotEmpty();
        }

        @Test
        @DisplayName("cross-field: same current and new password fails @PasswordsNotEqual")
        void samePasswords_fail_crossFieldConstraint() {
            var request = new ChangePasswordRequest("Password1!", "Password1!");
            Set<ConstraintViolation<ChangePasswordRequest>> violations = violationsFor(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"))
                    .anyMatch(v -> v.getMessage().contains("differ"));
        }

        @Test
        @DisplayName("cross-field: null fields skip @PasswordsNotEqual (field violations reported first)")
        void nullFields_skipCrossFieldCheck() {
            // Both fields null — @NotBlank fires, @PasswordsNotEqual skips
            var request = new ChangePasswordRequest(null, null);
            Set<ConstraintViolation<ChangePasswordRequest>> violations = violationsFor(request);

            // Only @NotBlank violations, no cross-field "must differ" message
            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .noneMatch(v -> v.getMessage().contains("differ"));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private <T> Set<ConstraintViolation<T>> violationsFor(T object) {
        return validator.validate(object);
    }

    private <T> Set<ConstraintViolation<T>> fieldViolations(T object, String fieldName) {
        return validator.validateProperty(object, fieldName);
    }
}
