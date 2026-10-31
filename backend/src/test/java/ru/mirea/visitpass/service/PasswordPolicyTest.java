package ru.mirea.visitpass.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

  @Test
  void tooShortThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> PasswordPolicy.validate("abc"));
  }

  @Test
  void exactlySevenCharsThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> PasswordPolicy.validate("Pass123"));
  }

  @Test
  void noDigitThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> PasswordPolicy.validate("password"));
  }

  @Test
  void noLetterThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> PasswordPolicy.validate("12345678"));
  }

  @Test
  void validPasswordPasses() {
    assertDoesNotThrow(() -> PasswordPolicy.validate("Pass1234"));
  }

  @Test
  void nullThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> PasswordPolicy.validate(null));
  }

  @Test
  void cyrillicLetterCountsAsLetter() {
    assertDoesNotThrow(() -> PasswordPolicy.validate("Пароль123"));
  }
}
