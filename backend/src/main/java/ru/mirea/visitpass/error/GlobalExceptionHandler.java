package ru.mirea.visitpass.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

  public static final String CORRELATION_ID_KEY = "correlationId";

  static ApiError body(HttpStatus status, String message,
      HttpServletRequest request) {
    return new ApiError(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI(),
        MDC.get(CORRELATION_ID_KEY));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> e.getField() + ": " + e.getDefaultMessage())
        .sorted()
        .findFirst()
        .orElse("Validation failed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(body(HttpStatus.BAD_REQUEST, message, request));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(body(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(body(HttpStatus.FORBIDDEN, ex.getMessage(), request));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(body(HttpStatus.FORBIDDEN, ex.getMessage(), request));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiError> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(body(HttpStatus.UNAUTHORIZED, ex.getMessage(), request));
  }

  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<ApiError> handleLocked(AccountLockedException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.valueOf(423))
        .body(body(HttpStatus.valueOf(423), ex.getMessage(), request));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(body(HttpStatus.NOT_FOUND, ex.getMessage(), request));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(body(HttpStatus.NOT_FOUND, "No such endpoint", request));
  }

  @ExceptionHandler(VersionConflictException.class)
  public ResponseEntity<ApiError> handleConflict(VersionConflictException ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(body(HttpStatus.CONFLICT, ex.getMessage(), request));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(body(HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error", request));
  }
}
