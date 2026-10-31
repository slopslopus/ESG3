package ru.mirea.visitpass.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mirea.visitpass.security.JwtPrincipal;
import ru.mirea.visitpass.security.JwtService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (!token.isBlank()) {
        try {
          Jws<Claims> parsed = jwtService.parse(token);
          Claims claims = parsed.getPayload();
          List<?> roles = claims.get("roles", List.class);
          List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
              : roles.stream()
                  .map(Object::toString)
                  .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                  .toList();
          JwtPrincipal principal = new JwtPrincipal(
              UUID.fromString(claims.get("uid", String.class)),
              claims.getSubject());
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(principal, null, authorities);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
          SecurityContextHolder.clearContext();
        }
      }
    }
    chain.doFilter(request, response);
  }
}
