package ru.mirea.visitpass.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.dto.auth.ChangePasswordRequest;
import ru.mirea.visitpass.dto.auth.LoginRequest;
import ru.mirea.visitpass.dto.auth.LoginResponse;
import ru.mirea.visitpass.dto.auth.RefreshRequest;
import ru.mirea.visitpass.dto.auth.TokenResponse;
import ru.mirea.visitpass.dto.auth.UserDto;
import ru.mirea.visitpass.security.CurrentUser;
import ru.mirea.visitpass.security.JwtPrincipal;
import ru.mirea.visitpass.service.AuthService;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body,
      HttpServletRequest request) {
    LoginResponse response = auth.login(body.email(), body.password(),
        request.getRemoteAddr(), request.getHeader("User-Agent"));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
    TokenResponse response = auth.refresh(body.refreshToken(),
        request.getRemoteAddr(), request.getHeader("User-Agent"));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal JwtPrincipal principal,
      @Valid @RequestBody RefreshRequest body) {
    UUID userUuid = principal == null ? null : principal.id();
    if (userUuid == null) {
      userUuid = CurrentUser.current().id();
    }
    auth.logout(body.refreshToken(), userUuid);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/me")
  public ResponseEntity<UserDto> me(@AuthenticationPrincipal JwtPrincipal principal) {
    UUID uuid = principal == null ? CurrentUser.current().id() : principal.id();
    return ResponseEntity.ok(UserDto.from(auth.requireUser(uuid)));
  }

  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal JwtPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest body) {
    UUID uuid = principal == null ? CurrentUser.current().id() : principal.id();
    auth.changePassword(uuid, body.oldPassword(), body.newPassword());
    return ResponseEntity.ok().build();
  }

}
