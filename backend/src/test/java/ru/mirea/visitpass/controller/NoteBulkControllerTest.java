package ru.mirea.visitpass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;
import ru.mirea.visitpass.repository.AuditEventRepository;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.RefreshSessionRepository;
import ru.mirea.visitpass.repository.UserAccountRepository;
import ru.mirea.visitpass.repository.VisitNoteRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;
import ru.mirea.visitpass.repository.VisitorRepository;
import ru.mirea.visitpass.service.RequestService;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:notebulkdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class NoteBulkControllerTest {

  @Autowired
  private org.springframework.transaction.PlatformTransactionManager
      txManager;

  private static final String PASSWORD = "Passw0rd1";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private RequestService requestService;

  @Autowired
  private UserAccountRepository users;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private EmployeeRepository employees;

  @Autowired
  private VisitorRepository visitors;

  @Autowired
  private VisitRequestRepository requests;

  @Autowired
  private VisitTagRepository tags;

  @Autowired
  private VisitNoteRepository noteRepo;

  @Autowired
  private AuditEventRepository audits;

  @Autowired
  private RefreshSessionRepository sessions;

  private UUID hostId;
  private String operToken;
  private String viewerToken;

  @BeforeEach
  void setUp() throws Exception {
    audits.deleteAll();
    sessions.deleteAll();
    noteRepo.deleteAll();
    requests.deleteAll();
    tags.deleteAll();
    visitors.deleteAll();
    employees.deleteAll();
    users.deleteAll();
    jdbcTemplate.execute(
        "CREATE SEQUENCE IF NOT EXISTS visit_request_code_seq START WITH 1");
    jdbcTemplate.execute(
        "ALTER SEQUENCE visit_request_code_seq RESTART WITH 1");
    jdbcTemplate.execute(
        "CREATE SEQUENCE IF NOT EXISTS pass_number_seq START WITH 1");
    jdbcTemplate.execute("ALTER SEQUENCE pass_number_seq RESTART WITH 1");
    saveUser("oper@test", "Operator", Set.of(UserRole.OPERATOR));
    saveUser("view@test", "Viewer", Set.of(UserRole.VIEWER));
    operToken = login("oper@test");
    viewerToken = login("view@test");
    Employee host = new Employee();
    host.setUuid(UUID.randomUUID());
    host.setFullName("Ivanov Ivan Ivanovich");
    host.setDepartment("Security");
    host.setRole(EmployeeRole.SECURITY);
    host.setActive(true);
    hostId = employees.save(host).getUuid();
  }

  private void saveUser(String email, String name, Set<UserRole> roles) {
    UserAccount user = new UserAccount();
    user.setUuid(UUID.randomUUID());
    user.setEmail(email);
    user.setName(name);
    user.setPasswordHash(passwordEncoder.encode(PASSWORD));
    user.setActive(true);
    user.setMustChangePassword(false);
    user.setFailedAttempts(0);
    user.setRoles(EnumSet.copyOf(roles));
    users.save(user);
  }

  private String login(String email) throws Exception {
    String body = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", email, "password", PASSWORD))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("accessToken").asText();
  }

  private String createRequest() {
    ru.mirea.visitpass.dto.request.CreateRequestDto dto =
        new ru.mirea.visitpass.dto.request.CreateRequestDto();
    dto.setVisitorFullName("Fedorov Fedor Fedorovich");
    dto.setVisitorDocumentType("PASSPORT");
    dto.setVisitorDocumentNumber("1234 567890");
    dto.setHostEmployeeId(hostId);
    dto.setPurpose("Contract signing meeting");
    dto.setVisitDate(LocalDate.now().plusDays(3));
    dto.setTimeFrom(java.time.LocalTime.of(10, 0));
    dto.setTimeTo(java.time.LocalTime.of(11, 0));
    return requestService.create(dto).getId().toString();
  }

  private String auth(String token) {
    return "Bearer " + token;
  }

  @Test
  void createNoteReturns201WithAuthorName() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "Guest arrived early");
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("Guest arrived early"))
        .andExpect(jsonPath("$.pinned").value(false))
        .andExpect(jsonPath("$.authorName").value("Operator"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void pinnedFlagTogglesOnUpdate() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "Escort required");
    payload.put("pinned", true);
    String body = mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pinned").value(true))
        .andReturn().getResponse().getContentAsString();
    String noteId =
        objectMapper.readTree(body).get("id").asText();
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("text", "Escort required");
    update.put("pinned", false);
    mockMvc.perform(put("/api/requests/notes/{noteId}", noteId)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pinned").value(false));
    mockMvc.perform(get("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].pinned").value(false));
  }

  @Test
  void noteTextBoundedAt2000() throws Exception {
    String uuid = createRequest();
    Map<String, Object> ok = new LinkedHashMap<>();
    ok.put("text", "x".repeat(2000));
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(ok)))
        .andExpect(status().isCreated());
    Map<String, Object> tooLong = new LinkedHashMap<>();
    tooLong.put("text", "x".repeat(2001));
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(tooLong)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void pinnedNotesListedFirst() throws Exception {
    String uuid = createRequest();
    Map<String, Object> plain = new LinkedHashMap<>();
    plain.put("text", "plain note");
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(plain)))
        .andExpect(status().isCreated());
    Map<String, Object> pinned = new LinkedHashMap<>();
    pinned.put("text", "pinned note");
    pinned.put("pinned", true);
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(pinned)))
        .andExpect(status().isCreated());
    mockMvc.perform(get("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].text").value("pinned note"))
        .andExpect(jsonPath("$[1].text").value("plain note"));
  }

  @Test
  void bulkAddTooLongTagIsBadRequest() throws Exception {
    String uuid = createRequest();
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid));
    bulk.put("operation", "ADD_TAG");
    bulk.put("tag", "t".repeat(51));
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void noteOnMissingRequestReturns404() throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "Orphan note");
    mockMvc.perform(post("/api/requests/{id}/notes", UUID.randomUUID())
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherOperatorCanEditNote() throws Exception {
    saveUser("oper2@test", "Second Operator", Set.of(UserRole.OPERATOR));
    String oper2 = login("oper2@test");
    String uuid = createRequest();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "First note");
    String body = mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String noteId =
        objectMapper.readTree(body).get("id").asText();
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("text", "Edited by second operator");
    mockMvc.perform(put("/api/requests/notes/{noteId}", noteId)
            .header("Authorization", "Bearer " + oper2)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Edited by second operator"));
    mockMvc.perform(delete("/api/requests/notes/{noteId}", noteId)
            .header("Authorization", "Bearer " + oper2))
        .andExpect(status().isNoContent());
  }

  @Test
  void viewerForbiddenOnPostNoteAndBulk() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "Viewer note attempt");
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(viewerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isForbidden());
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid));
    bulk.put("operation", "ARCHIVE");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(viewerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isForbidden());
  }

  @Test
  void bulkArchiveOneMissingYieldsOneSucceededOneFailed() throws Exception {
    String uuid = createRequest();
    UUID missing = UUID.randomUUID();
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid, missing.toString()));
    bulk.put("operation", "ARCHIVE");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded", hasSize(1)))
        .andExpect(jsonPath("$.failed", hasSize(1)))
        .andExpect(jsonPath("$.failed[0].id").value(missing.toString()))
        .andExpect(jsonPath("$.failed[0].error").isNotEmpty());
    assertTrue(requests.findByUuid(UUID.fromString(uuid)).orElseThrow()
        .isArchived());
  }

  @Test
  void bulkSetStatusInvalidStatusFailsNot500() throws Exception {
    String uuid = createRequest();
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid));
    bulk.put("operation", "SET_STATUS");
    bulk.put("status", "BOGUS");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded", hasSize(0)))
        .andExpect(jsonPath("$.failed", hasSize(1)))
        .andExpect(jsonPath("$.failed[0].error").isNotEmpty());
  }

  @Test
  void bulkAddAndRemoveTag() throws Exception {
    String uuid = createRequest();
    Map<String, Object> add = new LinkedHashMap<>();
    add.put("ids", java.util.List.of(uuid));
    add.put("operation", "ADD_TAG");
    add.put("tag", "  VIP  ");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(add)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded", hasSize(1)))
        .andExpect(jsonPath("$.failed", hasSize(0)));
    org.springframework.transaction.support.TransactionTemplate readTx =
        new org.springframework.transaction.support.TransactionTemplate(
            txManager);
    readTx.setReadOnly(true);
    assertTrue(Boolean.TRUE.equals(readTx.execute(status -> requests
        .findByUuid(UUID.fromString(uuid)).orElseThrow()
        .getTags().stream().anyMatch(t -> t.getName().equals("vip")))));
    Map<String, Object> remove = new LinkedHashMap<>();
    remove.put("ids", java.util.List.of(uuid));
    remove.put("operation", "REMOVE_TAG");
    remove.put("tag", "VIP");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(remove)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded", hasSize(1)));
    assertTrue(Boolean.TRUE.equals(readTx.execute(txStatus -> requests
        .findByUuid(UUID.fromString(uuid)).orElseThrow()
        .getTags().isEmpty())));
  }

  @Test
  void bulkSetStatusToCancelledSucceeds() throws Exception {
    String uuid = createRequest();
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid));
    bulk.put("operation", "SET_STATUS");
    bulk.put("status", "CANCELLED");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded", hasSize(1)))
        .andExpect(jsonPath("$.failed", hasSize(0)));
    assertEquals("CANCELLED", requests
        .findByUuid(UUID.fromString(uuid)).orElseThrow().getStatus().name());
  }

  @Test
  void bulkSetStatusToIssuedAssignsPassNumber() throws Exception {
    String uuid = createRequest();
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid));
    bulk.put("operation", "SET_STATUS");
    bulk.put("status", "ISSUED");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.succeeded", hasSize(1)))
        .andExpect(jsonPath("$.failed", hasSize(0)));
    String pass = requests.findByUuid(UUID.fromString(uuid)).orElseThrow()
        .getPassNumber();
    assertTrue(pass != null && pass.matches("^PASS-[0-9]{3,6}$"));
  }

  @Test
  void bulkEmptyIdsYields400() throws Exception {
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of());
    bulk.put("operation", "ARCHIVE");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void bulkSetStatusWithoutStatusYields400() throws Exception {
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(UUID.randomUUID().toString()));
    bulk.put("operation", "SET_STATUS");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void bulkAddTagWithoutTagYields400() throws Exception {
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(UUID.randomUUID().toString()));
    bulk.put("operation", "ADD_TAG");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void bulkUnknownOperationYields400() throws Exception {
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(UUID.randomUUID().toString()));
    bulk.put("operation", "BOGUS_OP");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void noteUpdateAndDeleteAlsoAudited() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "Audited note");
    String body = mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String noteId = objectMapper.readTree(body).get("id").asText();
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("text", "Updated text");
    mockMvc.perform(put("/api/requests/notes/{noteId}", noteId)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/requests/notes/{noteId}", noteId)
            .header("Authorization", auth(operToken)))
        .andExpect(status().isNoContent());
    assertEquals(1, audits.findAll().stream()
        .filter(e -> e.getEventType().equals("NOTE_UPDATED"))
        .count());
    assertEquals(1, audits.findAll().stream()
        .filter(e -> e.getEventType().equals("NOTE_DELETED"))
        .count());
  }

  @Test
  void missingNoteYields404() throws Exception {
    UUID missing = UUID.randomUUID();
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("text", "Ghost update");
    mockMvc.perform(put("/api/requests/notes/{noteId}", missing)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    mockMvc.perform(delete("/api/requests/notes/{noteId}", missing)
            .header("Authorization", auth(operToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void auditCapturesNoteCreatedAndBulkOperation() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("text", "Audited note");
    mockMvc.perform(post("/api/requests/{id}/notes", uuid)
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated());
    Map<String, Object> bulk = new LinkedHashMap<>();
    bulk.put("ids", java.util.List.of(uuid));
    bulk.put("operation", "ARCHIVE");
    mockMvc.perform(post("/api/requests/bulk")
            .header("Authorization", auth(operToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bulk)))
        .andExpect(status().isOk());
    assertEquals(1, audits.findAll().stream()
        .filter(e -> e.getEventType().equals("NOTE_CREATED"))
        .count());
    assertEquals(1, audits.findAll().stream()
        .filter(e -> e.getEventType().equals("BULK_OPERATION"))
        .count());
    assertTrue(audits.findAll().stream()
        .filter(e -> e.getEventType().equals("BULK_OPERATION"))
        .anyMatch(e -> e.getDescription() != null
            && e.getDescription().contains("ok=1")
            && e.getDescription().contains("fail=0")));
  }
}
