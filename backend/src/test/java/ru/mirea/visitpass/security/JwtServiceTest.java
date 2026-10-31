package ru.mirea.visitpass.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

  private static final String ISSUER = "test-issuer";

  private static final String SECRET = Base64.getEncoder().encodeToString(
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
      "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

  private final JwtService service = new JwtService(SECRET, ISSUER, 15);

  private UserAccount user() {
    UserAccount user = new UserAccount();
    user.setUuid(UUID.randomUUID());
    user.setEmail("operator@example.com");
    user.setName("Operator");
    user.getRoles().add(UserRole.OPERATOR);
    return user;
  }

  @Test
  void createdTokenParsesWithSubjectAndRoles() {
    UserAccount user = user();

    String token = service.createAccess(user);

    Jws<Claims> parsed = service.parse(token);
    assertEquals(user.getEmail(), parsed.getPayload().getSubject());
    assertEquals(user.getUuid().toString(), parsed.getPayload().get("uid", String.class));
    List<?> roles = parsed.getPayload().get("roles", List.class);
    assertTrue(roles.contains(UserRole.OPERATOR.name()));
  }

  @Test
  void tokenFromForeignSecretRejected() {
    String token = service.createAccess(user());
    JwtService foreign = new JwtService(OTHER_SECRET, ISSUER, 15);

    assertThrows(JwtException.class, () -> foreign.parse(token));
  }

  @Test
  void expiredTokenRejected() {
    JwtService noTtl = new JwtService(SECRET, ISSUER, -1);
    String token = noTtl.createAccess(user());

    assertThrows(JwtException.class, () -> service.parse(token));
  }
}
