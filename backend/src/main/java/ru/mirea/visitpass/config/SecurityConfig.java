package ru.mirea.visitpass.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.mirea.visitpass.error.ApiError;
import ru.mirea.visitpass.error.GlobalExceptionHandler;
import ru.mirea.visitpass.filter.CorrelationIdFilter;
import ru.mirea.visitpass.filter.JwtAuthFilter;
import ru.mirea.visitpass.filter.MustChangePasswordFilter;
import ru.mirea.visitpass.security.JwtService;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static void writeApiError(HttpServletResponse response, int status,
      String error, String message, String path, ObjectMapper mapper)
      throws IOException {
    ApiError body = new ApiError(Instant.now(), status, error, message, path,
        MDC.get(GlobalExceptionHandler.CORRELATION_ID_KEY));
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), body);
  }

  @Bean
  public JwtService jwtService(AppProperties properties) {
    return new JwtService(properties);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy(
        "ROLE_ADMIN > ROLE_OPERATOR\nROLE_OPERATOR > ROLE_VIEWER");
  }

  @Bean
  public CorrelationIdFilter correlationIdFilter() {
    return new CorrelationIdFilter();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http,
      JwtService jwtService, CorrelationIdFilter correlationIdFilter,
      ObjectMapper mapper) throws Exception {
    AuthenticationEntryPoint entryPoint = (request, response, ex) ->
        writeApiError(response, 401, "Unauthorized",
            "Authentication required", request.getRequestURI(), mapper);
    AccessDeniedHandler deniedHandler = (request, response, ex) ->
        writeApiError(response, 403, "Forbidden",
            "Access denied", request.getRequestURI(), mapper);
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(deniedHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/api/auth/login", "/api/auth/refresh").permitAll()
            .requestMatchers("/", "/index.html").permitAll()
            .requestMatchers(HttpMethod.GET, "/*.js", "/*.css", "/*.ico").permitAll()
            .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                "/v3/api-docs/**", "/api-docs/**").hasRole("ADMIN")
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
        .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new JwtAuthFilter(jwtService),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new MustChangePasswordFilter(jwtService, mapper),
            JwtAuthFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    Arrays.stream(properties.getCors().getAllowedOrigins().split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(config::addAllowedOrigin);
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
