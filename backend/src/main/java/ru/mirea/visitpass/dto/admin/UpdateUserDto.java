package ru.mirea.visitpass.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserDto(
    @Size(min = 2, max = 150) String name,
    @Email String email,
    Set<String> roles,
    Boolean active) {
}
