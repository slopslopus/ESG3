package ru.mirea.visitpass.error;

public class VersionConflictException extends RuntimeException {

  public VersionConflictException(String message) {
    super(message);
  }
}
