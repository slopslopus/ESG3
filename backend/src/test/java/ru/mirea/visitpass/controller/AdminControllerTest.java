package ru.mirea.visitpass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.AuditEvent;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;
import ru.mirea.visitpass.repository.AuditEventRepository;
import ru.mirea.visitpass.repository.UserAccountRepository;
import ru.mirea.visitpass.service.PasswordPolicy;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admintestdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserAccountRepository users;

  @Autowired
  private AuditEventRepository auditEvents;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String adminEmail;

  @BeforeEach
  void setUp() {
    auditEvents.deleteAll();
    users.deleteAll();
    adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
    UserAccount admin = account(adminEmail, "Admin",
        EnumSet.of(UserRole.ADMIN), true);
    users.save(admin);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void listUsersReturnsDefaultPage() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value(adminEmail))
        .andExpect(jsonPath("$.content[0].roles").isArray())
        .andExpect(jsonPath("$.content[0].active").value(true));
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  void listUsersAsOperatorDenied() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void createUserReturns201AndPolicyCompliantPassword() throws Exception {
    Map<String, Object> body = Map.of(
        "email", "op-" + UUID.randomUUID() + "@example.com",
        "name", "Operator",
        "roles", Set.of("OPERATOR"));
    String response = mockMvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mustChangePassword").value(true))
        .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    String temporaryPassword =
        objectMapper.readTree(response).get("temporaryPassword").asText();
    PasswordPolicy.validate(temporaryPassword);
    UserAccount saved = users
        .findByEmail((String) body.get("email")).orElseThrow();
    assertTrue(passwordEncoder.matches(temporaryPassword,
        saved.getPasswordHash()));
    assertTrue(saved.isMustChangePassword());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void freshUserTokenBlockedOnRequestsUntilPasswordChange() throws Exception {
    String email = "fresh-" + UUID.randomUUID() + "@example.com";
    Map<String, Object> body = Map.of(
        "email", email,
        "name", "Fresh",
        "roles", Set.of("VIEWER"));
    String created = mockMvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String temporaryPassword =
        objectMapper.readTree(created).get("temporaryPassword").asText();
    String login = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", email, "password", temporaryPassword))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String access =
        objectMapper.readTree(login).get("accessToken").asText();
    mockMvc.perform(get("/api/requests")
            .header("Authorization", "Bearer " + access))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Password change required"));
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  void createUserAsOperatorIsForbidden() throws Exception {
    Map<String, Object> body = Map.of(
        "email", "op-" + UUID.randomUUID() + "@example.com",
        "name", "Operator",
        "roles", Set.of("OPERATOR"));
    mockMvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void createUserWithDuplicateEmailIsBadRequest() throws Exception {
    Map<String, Object> body = Map.of(
        "email", adminEmail,
        "name", "Clone",
        "roles", Set.of("VIEWER"));
    mockMvc.perform(post("/api/admin/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Email already exists")));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void updateUserChangesNameRolesAndActive() throws Exception {
    UserAccount target = users.save(account(
        "op-" + UUID.randomUUID() + "@example.com",
        "Old Name", EnumSet.of(UserRole.VIEWER), true));
    Map<String, Object> body = Map.of(
        "name", "New Name",
        "roles", Set.of("OPERATOR"),
        "active", false);
    mockMvc.perform(put("/api/admin/users/" + target.getUuid())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New Name"))
        .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void resetPasswordRotatesHash() throws Exception {
    UserAccount target = users.save(account(
        "op-" + UUID.randomUUID() + "@example.com",
        "Operator", EnumSet.of(UserRole.OPERATOR), true));
    String oldHash = target.getPasswordHash();
    String response = mockMvc
        .perform(post("/api/admin/users/" + target.getUuid()
            + "/reset-password"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    String temporaryPassword =
        objectMapper.readTree(response).get("temporaryPassword").asText();
    PasswordPolicy.validate(temporaryPassword);
    UserAccount reloaded = users.findByUuid(target.getUuid()).orElseThrow();
    assertNotEquals(oldHash, reloaded.getPasswordHash());
    assertTrue(passwordEncoder.matches(temporaryPassword,
        reloaded.getPasswordHash()));
    assertFalse(passwordEncoder.matches("OldPass123",
        reloaded.getPasswordHash()));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void auditSearchFiltersAndPaginates() throws Exception {
    seedAudit("AUTH_LOGIN", "USER", "user signed in", "corr-1",
        LocalDateTime.now().minusHours(2));
    seedAudit("REQUEST_CREATED", "REQUEST", "request created", "corr-2",
        LocalDateTime.now().minusHours(1));
    seedAudit("AUTH_FAILED", "USER", "login failed", "corr-3",
        LocalDateTime.now());
    mockMvc.perform(get("/api/admin/audit")
            .param("q", "failed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].eventType").value("AUTH_FAILED"));
    mockMvc.perform(get("/api/admin/audit")
            .param("eventType", "REQUEST_CREATED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].entityType").value("REQUEST"));
    mockMvc.perform(get("/api/admin/audit")
            .param("size", "1")
            .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void auditDefaultsToCreatedAtDesc() throws Exception {
    seedAudit("AUTH_LOGIN", "USER", "first", "corr-1",
        LocalDateTime.now().minusHours(2));
    seedAudit("AUTH_FAILED", "USER", "second", "corr-2",
        LocalDateTime.now());
    mockMvc.perform(get("/api/admin/audit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].description").value("second"))
        .andExpect(jsonPath("$.content[1].description").value("first"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void auditIncludesUsernameForNonNullUser() throws Exception {
    UserAccount actor = users.save(account(
        "actor-" + UUID.randomUUID() + "@example.com",
        "Actor", EnumSet.of(UserRole.OPERATOR), true));
    AuditEvent event = new AuditEvent();
    event.setUser(actor);
    event.setEventType("REQUEST_CREATED");
    event.setEntityType("REQUEST");
    event.setEntityUuid(UUID.randomUUID());
    event.setDescription("created by actor");
    event.setCorrelationId("corr-actor");
    event.setCreatedAt(LocalDateTime.now());
    auditEvents.save(event);
    mockMvc.perform(get("/api/admin/audit")
            .param("q", "actor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].username").value(actor.getEmail()));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void auditSortsByEventTypeAsc() throws Exception {
    seedAudit("REQUEST_CREATED", "REQUEST", "gamma", "corr-1",
        LocalDateTime.now().minusHours(2));
    seedAudit("AUTH_FAILED", "USER", "beta", "corr-2",
        LocalDateTime.now().minusHours(1));
    seedAudit("AUTH_LOGIN", "USER", "alpha", "corr-3",
        LocalDateTime.now());
    mockMvc.perform(get("/api/admin/audit")
            .param("sort", "eventType")
            .param("dir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].eventType").value("AUTH_FAILED"))
        .andExpect(jsonPath("$.content[2].eventType").value("REQUEST_CREATED"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void auditDirAscDescFlipsOrder() throws Exception {
    seedAudit("AUTH_LOGIN", "USER", "older", "corr-1",
        LocalDateTime.now().minusHours(2));
    seedAudit("AUTH_FAILED", "USER", "newer", "corr-2",
        LocalDateTime.now());
    mockMvc.perform(get("/api/admin/audit")
            .param("sort", "createdAt")
            .param("dir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].description").value("older"))
        .andExpect(jsonPath("$.content[1].description").value("newer"));
    mockMvc.perform(get("/api/admin/audit")
            .param("sort", "createdAt")
            .param("dir", "desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].description").value("newer"))
        .andExpect(jsonPath("$.content[1].description").value("older"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void auditUnknownSortIsBadRequest() throws Exception {
    mockMvc.perform(get("/api/admin/audit")
            .param("sort", "passwordHash"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("Unknown sort field")));
  }

  private UserAccount account(String email, String name, Set<UserRole> roles,
      boolean active) {
    UserAccount user = new UserAccount();
    user.setUuid(UUID.randomUUID());
    user.setEmail(email);
    user.setName(name);
    user.setPasswordHash(passwordEncoder.encode("OldPass123"));
    user.setActive(active);
    user.setMustChangePassword(false);
    user.setFailedAttempts(0);
    user.setRoles(EnumSet.copyOf(roles));
    return user;
  }

  private void seedAudit(String eventType, String entityType,
      String description, String correlationId, LocalDateTime createdAt) {
    AuditEvent event = new AuditEvent();
    event.setUser(null);
    event.setEventType(eventType);
    event.setEntityType(entityType);
    event.setEntityUuid(UUID.randomUUID());
    event.setDescription(description);
    event.setCorrelationId(correlationId);
    event.setCreatedAt(createdAt);
    auditEvents.save(event);
  }
}
