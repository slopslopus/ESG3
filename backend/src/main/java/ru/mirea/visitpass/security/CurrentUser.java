package ru.mirea.visitpass.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

public record CurrentUser(UUID id, String email, List<String> roles) {

  public static CurrentUser from(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
      throw new IllegalStateException("No authenticated user in security context");
    }
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .sorted()
        .toList();
    return new CurrentUser(principal.id(), principal.email(), roles);
  }

  public static CurrentUser current() {
    return from(SecurityContextHolder.getContext().getAuthentication());
  }
}
