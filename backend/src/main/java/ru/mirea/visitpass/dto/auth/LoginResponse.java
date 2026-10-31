package ru.mirea.visitpass.dto.auth;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    UserDto user,
    boolean mustChangePassword) {
}
