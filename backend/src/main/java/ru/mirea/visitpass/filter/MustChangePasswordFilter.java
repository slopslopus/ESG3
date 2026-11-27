package ru.mirea.visitpass.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mirea.visitpass.error.ApiError;
import ru.mirea.visitpass.error.GlobalExceptionHandler;
import ru.mirea.visitpass.security.JwtService;

import java.io.IOException;
import java.time.Instant;
/**
 * Blocks authenticated requests carrying a stale must-change-password access
 * token for every endpoint except {@code /api/auth/me},
 * {@code /api/auth/change-password} and {@code /api/config}. The flag
 * travels in the {@code mcp}
 * JWT claim issued at login/refresh time; after a password change the client
 * must refresh to obtain an {@code mcp:false} token (the old access token
 * expires within access-minutes at most).
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

  static final String MCP_CLAIM = "mcp";

  static final String DENY_MESSAGE = "Password change required";

  private final JwtService jwtService;

  private final ObjectMapper mapper;

  public MustChangePasswordFilter(JwtService jwtService, ObjectMapper mapper) {
    this.jwtService = jwtService;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (isGuarded(path)) {
      String header = request.getHeader("Authorization");
      if (header != null && header.startsWith("Bearer ")) {
        String token = header.substring(7);
        if (!token.isBlank()) {
          try {
            Jws<Claims> parsed = jwtService.parse(token);
            Boolean mcp = parsed.getPayload().get(MCP_CLAIM, Boolean.class);
            if (Boolean.TRUE.equals(mcp)) {
              response.setStatus(HttpServletResponse.SC_FORBIDDEN);
              response.setContentType("application/json");
              mapper.writeValue(response.getWriter(),
                  new ApiError(Instant.now(), 403, "Forbidden",
                      DENY_MESSAGE, path,
                      MDC.get(GlobalExceptionHandler.CORRELATION_ID_KEY)));
              return;
            }
          } catch (RuntimeException ex) {
            // Not our token problem: JwtAuthFilter already cleared context,
            // entry point produces 401 downstream.
          }
        }
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean isGuarded(String path) {
    if (!path.startsWith("/api/")) {
      return false;
    }
    return !path.equals("/api/auth/me")
        && !path.equals("/api/auth/change-password")
        && !path.equals("/api/config");
  }
}
