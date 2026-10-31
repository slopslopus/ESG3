package ru.mirea.visitpass.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserTest {

  @Test
  void rolesArePlainNamesWithoutPrefix() {
    UUID id = UUID.randomUUID();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(id, "operator@example.com"), null,
            List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

    CurrentUser user = CurrentUser.from(authentication);

    assertEquals(id, user.id());
    assertEquals("operator@example.com", user.email());
    assertEquals(List.of("OPERATOR"), user.roles());
  }

  @Test
  void missingAuthenticationThrows() {
    assertThrows(IllegalStateException.class, () -> CurrentUser.from(null));
  }
}
