package ru.mirea.visitpass.dto.auth;

public record TokenResponse(
    String accessToken,
    String refreshToken) {
}
