package ru.mirea.visitpass.dto.admin;

import java.util.Set;
import java.util.UUID;

public record CreateUserResponse(
    UUID id,
    String email,
    String name,
    Set<String> roles,
    boolean active,
    boolean mustChangePassword,
    String temporaryPassword) {
}
