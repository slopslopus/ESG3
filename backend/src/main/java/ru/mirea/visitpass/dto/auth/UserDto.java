package ru.mirea.visitpass.dto.auth;

import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;

import java.util.List;

public record UserDto(
    String id,
    String email,
    String name,
    List<String> roles,
    boolean active,
    boolean mustChangePassword) {

  public static UserDto from(UserAccount user) {
    List<String> roles = user.getRoles().stream()
        .map(UserRole::name)
        .sorted()
        .toList();
    return new UserDto(
        user.getUuid().toString(),
        user.getEmail(),
        user.getName(),
        roles,
        user.isActive(),
        user.isMustChangePassword());
  }
}
