package ru.mirea.visitpass.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateUserDto(
    @NotBlank @Email String email,
    @NotBlank String name,
    @NotEmpty Set<String> roles) {
}
