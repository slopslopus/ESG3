package ru.mirea.visitpass.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.config.AppProperties;
import ru.mirea.visitpass.domain.RefreshSession;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.error.InvalidCredentialsException;
import ru.mirea.visitpass.repository.RefreshSessionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class RefreshTokenService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final RefreshSessionRepository sessions;

  private final AppProperties properties;

  public RefreshTokenService(RefreshSessionRepository sessions,
      AppProperties properties) {
    this.sessions = sessions;
    this.properties = properties;
  }

  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  @Transactional
  public CreatedSession create(UserAccount user, String ip, String userAgent) {
    byte[] random = new byte[32];
    RANDOM.nextBytes(random);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    RefreshSession session = new RefreshSession();
    session.setUser(user);
    session.setTokenHash(hash(rawToken));
    session.setCreatedAt(LocalDateTime.now());
    session.setExpiresAt(LocalDateTime.now()
        .plusDays(properties.getJwt().getRefreshDays()));
    session.setIp(ip);
    session.setUserAgent(userAgent);
    sessions.save(session);
    return new CreatedSession(rawToken, session);
  }

  @Transactional
  public RefreshSession findValid(String rawToken) {
    RefreshSession session = sessions.findByTokenHash(hash(rawToken))
        .filter(s -> s.getRevokedAt() == null)
        .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
        .filter(s -> s.getUser().isActive())
        .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
    return session;
  }

  @Transactional
  public CreatedSession rotate(String rawToken, String ip, String userAgent) {
    RefreshSession session = findValid(rawToken);
    return rotateValid(session.getUser(), rawToken, ip, userAgent);
  }

  @Transactional
  public CreatedSession rotateValid(UserAccount user, String rawToken,
      String ip, String userAgent) {
    RefreshSession session = sessions.findByTokenHash(hash(rawToken))
        .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
    session.setRevokedAt(LocalDateTime.now());
    sessions.save(session);
    return create(user, ip, userAgent);
  }

  @Transactional
  public void revoke(String rawToken) {
    sessions.findByTokenHash(hash(rawToken)).ifPresent(session -> {
      if (session.getRevokedAt() == null) {
        session.setRevokedAt(LocalDateTime.now());
        sessions.save(session);
      }
    });
  }

  @Transactional
  public void revokeAll(UserAccount user) {
    List<RefreshSession> active = sessions.findByUserAndRevokedAtIsNull(user);
    for (RefreshSession session : active) {
      session.setRevokedAt(LocalDateTime.now());
      sessions.save(session);
    }
  }

  public record CreatedSession(String rawToken, RefreshSession session) {
  }
}
