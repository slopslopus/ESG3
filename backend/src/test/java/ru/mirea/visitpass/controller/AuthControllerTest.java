package ru.mirea.visitpass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.AuditEvent;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;
import ru.mirea.visitpass.repository.AuditEventRepository;
import ru.mirea.visitpass.repository.RefreshSessionRepository;
import ru.mirea.visitpass.repository.UserAccountRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@Transactional
class AuthControllerTest {

  private static final String PASSWORD = "Password1";

  private static final String INVALID_MESSAGE = "Invalid email or password";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserAccountRepository users;

  @Autowired
  private RefreshSessionRepository sessions;

  @Autowired
  private AuditEventRepository audits;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private UserAccount admin;

  private UserAccount mcpUser;

  private UserAccount inactive;

  @BeforeEach
  void seed() {
    audits.deleteAll();
    sessions.deleteAll();
    users.deleteAll();
    admin = save("admin@test", "Admin", Set.of(UserRole.ADMIN), true, false);
    save("oper@test", "Operator", Set.of(UserRole.OPERATOR), true, false);
    save("view@test", "Viewer", Set.of(UserRole.VIEWER), true, false);
    mcpUser = save("mcp@test", "Must Change", Set.of(UserRole.VIEWER), true, true);
    inactive = save("off@test", "Inactive", Set.of(UserRole.VIEWER), false, false);
  }

  private UserAccount save(String email, String name, Set<UserRole> roles,
      boolean active, boolean mustChangePassword) {
    UserAccount user = new UserAccount();
    user.setUuid(UUID.randomUUID());
    user.setEmail(email);
    user.setName(name);
    user.setPasswordHash(passwordEncoder.encode(PASSWORD));
    user.setActive(active);
    user.setMustChangePassword(mustChangePassword);
    user.setFailedAttempts(0);
    user.setRoles(EnumSet.copyOf(roles));
    return users.save(user);
  }

  private JsonNode loginBody(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", email, "password", password))))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void loginOkReturnsTokensAndUser() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "admin@test", "password", PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.user.email").value("admin@test"))
        .andExpect(jsonPath("$.user.name").value("Admin"))
        .andExpect(jsonPath("$.user.id").value(admin.getUuid().toString()))
        .andExpect(jsonPath("$.user.roles", hasItem("ADMIN")))
        .andExpect(jsonPath("$.user.active").value(true))
        .andExpect(jsonPath("$.user.mustChangePassword").value(false))
        .andExpect(jsonPath("$.mustChangePassword").value(false))
        .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertNotEquals(body.get("accessToken").asText(), body.get("refreshToken").asText());
  }

  @Test
  void wrongPasswordReturnsUniform401() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "admin@test", "password", "Wrongpass1"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
  }

  @Test
  void unknownEmailReturnsSame401() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "ghost@test", "password", "Wrongpass1"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
  }

  @Test
  void fiveFailuresLockAccountOnSixth() throws Exception {
    for (int i = 0; i < 5; i++) {
      mockMvc.perform(post("/api/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(
                  Map.of("email", "oper@test", "password", "Wrongpass1"))))
          .andExpect(status().isUnauthorized());
    }
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "oper@test", "password", "Wrongpass1"))))
        .andExpect(status().is(423));
    UserAccount locked = users.findByEmail("oper@test").orElseThrow();
    assertNotNull(locked.getLockedUntil());
    assertEquals(5, locked.getFailedAttempts());
  }

  @Test
  void refreshRotatesAndOldTokenDies() throws Exception {
    JsonNode first = loginBody("admin@test", PASSWORD);
    String refresh = first.get("refreshToken").asText();

    MvcResult result = mockMvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andReturn();
    JsonNode second = objectMapper.readTree(result.getResponse().getContentAsString());
    assertNotEquals(refresh, second.get("refreshToken").asText());

    mockMvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesSession() throws Exception {
    JsonNode login = loginBody("admin@test", PASSWORD);
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();

    mockMvc.perform(post("/api/auth/logout")
            .header("Authorization", "Bearer " + access)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void meRequiresTokenAndReturnsProfile() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized());

    JsonNode login = loginBody("oper@test", PASSWORD);
    String access = login.get("accessToken").asText();

    mockMvc.perform(get("/api/auth/me")
            .header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("oper@test"))
        .andExpect(jsonPath("$.name").value("Operator"))
        .andExpect(jsonPath("$.roles", hasItem("OPERATOR")))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.mustChangePassword").value(false))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void changePasswordRotatesCredentials() throws Exception {
    JsonNode login = loginBody("view@test", PASSWORD);
    String access = login.get("accessToken").asText();
    String staleRefresh = login.get("refreshToken").asText();

    mockMvc.perform(post("/api/auth/change-password")
            .header("Authorization", "Bearer " + access)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("oldPassword", PASSWORD, "newPassword", "Newpass2"))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("refreshToken", staleRefresh))))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "view@test", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());

    loginBody("view@test", "Newpass2");
  }

  @Test
  void changePasswordRejectsWeakNewPassword() throws Exception {
    JsonNode login = loginBody("view@test", PASSWORD);
    String access = login.get("accessToken").asText();

    mockMvc.perform(post("/api/auth/change-password")
            .header("Authorization", "Bearer " + access)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("oldPassword", PASSWORD, "newPassword", "short"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changePasswordRejectsWrongOldPassword() throws Exception {
    JsonNode login = loginBody("view@test", PASSWORD);
    String access = login.get("accessToken").asText();

    mockMvc.perform(post("/api/auth/change-password")
            .header("Authorization", "Bearer " + access)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("oldPassword", "Wrongpass1", "newPassword", "Newpass2"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Current password is incorrect"));
  }

  @Test
  void mustChangePasswordBlocksRefreshButAllowsMe() throws Exception {
    JsonNode login = loginBody("mcp@test", PASSWORD);
    assertTrue(login.get("mustChangePassword").asBoolean());
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();

    mockMvc.perform(get("/api/auth/me")
            .header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));

    mockMvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Password change required"));
  }

  @Test
  void mustChangePasswordGuardsOtherEndpoints() throws Exception {
    JsonNode login = loginBody("mcp@test", PASSWORD);
    String access = login.get("accessToken").asText();

    mockMvc.perform(get("/api/admin/users")
            .header("Authorization", "Bearer " + access))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Password change required"));

    mockMvc.perform(post("/api/auth/change-password")
            .header("Authorization", "Bearer " + access)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("oldPassword", PASSWORD, "newPassword", "Newpass2"))))
        .andExpect(status().isOk());
  }

  @Test
  void inactiveUserCannotLogin() throws Exception {
    assertEquals("off@test", inactive.getEmail());
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "off@test", "password", PASSWORD))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
  }

  @Test
  void authEventsAreAudited() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", "admin@test", "password", "Wrongpass1"))))
        .andExpect(status().isUnauthorized());

    JsonNode login = loginBody("admin@test", PASSWORD);

    mockMvc.perform(post("/api/auth/logout")
            .header("Authorization", "Bearer " + login.get("accessToken").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("refreshToken", login.get("refreshToken").asText()))))
        .andExpect(status().isOk());

    List<String> types = audits.findAll().stream()
        .map(AuditEvent::getEventType)
        .toList();
    assertTrue(types.contains("AUTH_FAILED"));
    assertTrue(types.contains("AUTH_LOGIN"));
    assertTrue(types.contains("AUTH_LOGOUT"));
  }
}
