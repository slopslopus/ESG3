package ru.mirea.visitpass.service;

public final class PasswordPolicy {

  public static final int MIN_LENGTH = 8;

  private PasswordPolicy() {
  }

  public static void validate(String value) {
    if (value == null || value.length() < MIN_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be at least 8 characters long");
    }
    boolean hasLetter = value.codePoints().anyMatch(Character::isLetter);
    boolean hasDigit = value.codePoints().anyMatch(Character::isDigit);
    if (!hasLetter || !hasDigit) {
      throw new IllegalArgumentException(
          "Password must contain at least one letter and one digit");
    }
  }
}
