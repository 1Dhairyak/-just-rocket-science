package com.justrocketscience.auth.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.justrocketscience.auth.dto.request.ChangePasswordRequest;

/**
 * Class-level constraint for {@link ChangePasswordRequest}.
 * Verifies that {@code currentPassword} and {@code newPassword} are not identical.
 *
 * <h3>Why class-level, not field-level</h3>
 * Cross-field validation must see both fields simultaneously. Jakarta Validation
 * doesn't support comparing two fields with a single field-level annotation.
 * A class-level constraint receives the entire record instance.
 *
 * <h3>Validation ordering</h3>
 * Jakarta runs field-level constraints first. If either field is blank or too long,
 * those violations are reported and this class-level constraint is skipped.
 * This prevents the confusing combination of "field is required" AND
 * "fields must not be equal" appearing for a blank field.
 *
 * <h3>Error node targeting</h3>
 * The violation is added to the {@code newPassword} node specifically, not the
 * class level. This means API clients receive:
 * <pre>
 *   { "field": "newPassword", "message": "New password must differ from current password" }
 * </pre>
 * rather than a class-level error with no field association, which most
 * frontend validation libraries can't bind to a specific input.
 */
@Documented
@Constraint(validatedBy = PasswordsNotEqual.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsNotEqual {

    String message() default "New password must differ from the current password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordsNotEqual, ChangePasswordRequest> {

        @Override
        public boolean isValid(ChangePasswordRequest request,
                               ConstraintValidatorContext context) {
            // Skip cross-field check if either field failed its own constraints.
            // Jakarta calls isValid even when fields are null (if @NotNull is absent),
            // so we guard explicitly.
            if (request.currentPassword() == null || request.newPassword() == null) {
                return true; // field-level @NotBlank already reported the violation
            }

            boolean passwordsAreEqual = request.currentPassword()
                                               .equals(request.newPassword());

            if (passwordsAreEqual) {
                // Disable the default class-level message and attach to newPassword field
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                               "New password must differ from the current password")
                       .addPropertyNode("newPassword")
                       .addConstraintViolation();
                return false;
            }

            return true;
        }
    }
}
