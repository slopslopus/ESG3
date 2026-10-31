package ru.mirea.visitpass.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * Mirrors DB {@code CHECK (visit_date <= CURRENT_DATE + 6 months)} at the
 * Bean Validation layer so violations surface as 400, not 500.
 */
@Documented
@Constraint(validatedBy = VisitDateRange.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VisitDateRange {

  String message() default "visitDate must be within 6 months from today";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<VisitDateRange, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext ctx) {
      if (value == null) {
        return true;
      }
      return !value.isAfter(LocalDate.now().plusMonths(6));
    }
  }
}
