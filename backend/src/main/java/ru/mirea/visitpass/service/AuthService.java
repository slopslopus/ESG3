package ru.mirea.visitpass.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.dto.auth.LoginResponse;
import ru.mirea.visitpass.dto.auth.TokenResponse;
import ru.mirea.visitpass.dto.auth.UserDto;
import ru.mirea.visitpass.error.AccountLockedException;
import ru.mirea.visitpass.error.ForbiddenException;
import ru.mirea.visitpass.error.InvalidCredentialsException;
import ru.mirea.visitpass.repository.UserAccountRepository;
import ru.mirea.visitpass.security.JwtService;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

  public static final String INVALID_MESSAGE = "Invalid email or password";

  private static final int MAX_FAILURES = 5;

  private static final int LOCK_MINUTES = 15;

  private final UserAccountRepository users;

  private final PasswordEncoder passwordEncoder;

  private final JwtService jwtService;

  private final RefreshTokenService refreshTokens;

  private final AuditService audits;

  public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder,
      JwtService jwtService, RefreshTokenService refreshTokens,
      AuditService audits) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokens = refreshTokens;
    this.audits = audits;
  }

  @Transactional(noRollbackFor = {InvalidCredentialsException.class,
      AccountLockedException.class})
  public LoginResponse login(String email, String password, String ip,
      String userAgent) {
    UserAccount user = users.findByEmail(email).orElse(null);
    if (user == null || !user.isActive()) {
      audits.record(AuditService.AUTH_FAILED, "user", null,
          "Failed login for email=" + email);
      throw new InvalidCredentialsException(INVALID_MESSAGE);
    }
    if (user.getLockedUntil() != null
        && user.getLockedUntil().isAfter(LocalDateTime.now())) {
      throw new AccountLockedException(
          "Account locked until " + user.getLockedUntil());
    }
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      int attempts = user.getFailedAttempts() + 1;
      user.setFailedAttempts(attempts);
      if (attempts >= MAX_FAILURES) {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
      }
      users.save(user);
      audits.record(AuditService.AUTH_FAILED, "user", user.getUuid(),
          "Failed login for email=" + email);
      throw new InvalidCredentialsException(INVALID_MESSAGE);
    }
    user.setFailedAttempts(0);
    user.setLockedUntil(null);
    String access = jwtService.createAccess(user,
        user.isMustChangePassword());
    RefreshTokenService.CreatedSession created =
        refreshTokens.create(user, ip, userAgent);
    audits.record(AuditService.AUTH_LOGIN, "user", user.getUuid(),
        "Login for email=" + email);
    return new LoginResponse(access, created.rawToken(), UserDto.from(user),
        user.isMustChangePassword());
  }
  @Transactional
  public TokenResponse refresh(String rawToken, String ip, String userAgent) {
    UserAccount user = refreshTokens.findValid(rawToken).getUser();
    requirePasswordChangeClear(user);
    RefreshTokenService.CreatedSession created =
        refreshTokens.rotateValid(user, rawToken, ip, userAgent);
    String access = jwtService.createAccess(user,
        user.isMustChangePassword());
    return new TokenResponse(access, created.rawToken());
  }

  @Transactional
  public void logout(String rawToken, UUID userUuid) {
    refreshTokens.revoke(rawToken);
    audits.record(AuditService.AUTH_LOGOUT, "user", userUuid, "Logout");
  }

  @Transactional(readOnly = true)
  public UserAccount requireUser(UUID uuid) {
    return users.findByUuid(uuid)
        .orElseThrow(() -> new InvalidCredentialsException(INVALID_MESSAGE));
  }

  @Transactional
  public void changePassword(UUID uuid, String oldPassword, String newPassword) {
    UserAccount user = requireUser(uuid);
    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }
    PasswordPolicy.validate(newPassword);
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setMustChangePassword(false);
    user.setFailedAttempts(0);
    user.setLockedUntil(null);
    users.save(user);
    refreshTokens.revokeAll(user);
    audits.record(AuditService.AUTH_PASSWORD_CHANGED, "user", user.getUuid(),
        "Password changed");
  }

  public void requirePasswordChangeClear(UserAccount user) {
    if (user.isMustChangePassword()) {
      throw new ForbiddenException("Password change required");
    }
  }
}
