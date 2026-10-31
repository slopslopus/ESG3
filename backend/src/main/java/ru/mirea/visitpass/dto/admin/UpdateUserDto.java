package ru.mirea.visitpass.dto.admin;

import jakarta.validation.constraints.Email;

import java.util.Set;

public record UpdateUserDto(
    String name,
    @Email String email,
    Set<String> roles,
    Boolean active) {
}
