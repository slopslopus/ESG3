package ru.mirea.visitpass.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ru.mirea.visitpass.config.AppProperties;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class JwtService {

  private final SecretKey key;
  private final String issuer;
  private final int accessMinutes;

  public JwtService(String secretBase64, String issuer, int accessMinutes) {
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
    this.issuer = issuer;
    this.accessMinutes = accessMinutes;
  }

  public JwtService(AppProperties properties) {
    this(properties.getJwt().getSecretBase64(),
        properties.getJwt().getIssuer(),
        properties.getJwt().getAccessMinutes());
  }

  public String createAccess(UserAccount user) {
    return createAccess(user, user.isMustChangePassword());
  }

  public String createAccess(UserAccount user, boolean mustChangePassword) {
    Instant now = Instant.now();
    List<String> roles = user.getRoles().stream()
        .map(UserRole::name)
        .sorted()
        .toList();
    return Jwts.builder()
        .issuer(issuer)
        .subject(user.getEmail())
        .claim("uid", user.getUuid().toString())
        .claim("roles", roles)
        .claim("mcp", mustChangePassword)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessMinutes * 60L)))
        .signWith(key)
        .compact();
  }

  public Jws<Claims> parse(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .requireIssuer(issuer)
        .build()
        .parseSignedClaims(token);
  }
}
