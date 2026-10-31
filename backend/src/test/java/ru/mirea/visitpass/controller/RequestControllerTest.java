package ru.mirea.visitpass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.mirea.visitpass.domain.DocumentType;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;
import ru.mirea.visitpass.repository.VisitorRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:requestsdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class RequestControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private VisitRequestRepository requests;

  @Autowired
  private EmployeeRepository employees;

  @Autowired
  private VisitorRepository visitors;

  @Autowired
  private VisitTagRepository tags;

  @Autowired
  private ru.mirea.visitpass.service.RequestService requestService;

  private UUID hostId;

  @BeforeEach
  void setUp() {
    requests.deleteAll();
    tags.deleteAll();
    visitors.deleteAll();
    employees.deleteAll();
    jdbcTemplate.execute(
        "CREATE SEQUENCE IF NOT EXISTS visit_request_code_seq START WITH 1");
    jdbcTemplate.execute(
        "ALTER SEQUENCE visit_request_code_seq RESTART WITH 1");
    Employee host = new Employee();
    host.setUuid(UUID.randomUUID());
    host.setFullName("Ivanov Ivan Ivanovich");
    host.setDepartment("Security");
    host.setRole(EmployeeRole.SECURITY);
    host.setActive(true);
    hostId = employees.save(host).getUuid();
  }

  private Map<String, Object> createPayload() {
    return createPayload(hostId);
  }

  private Map<String, Object> createPayload(UUID host) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("visitorFullName", "Fedorov Fedor Fedorovich");
    payload.put("visitorDocumentType", "PASSPORT");
    payload.put("visitorDocumentNumber", "1234 567890");
    payload.put("hostEmployeeId", host.toString());
    payload.put("purpose", "Contract signing meeting");
    payload.put("visitDate", LocalDate.now().plusDays(3).toString());
    payload.put("timeFrom", "10:00");
    payload.put("timeTo", "11:00");
    return payload;
  }

  private String createRequest() {
    return createRequest(createPayload());
  }

  private String createRequest(Map<String, Object> payload) {
    ru.mirea.visitpass.dto.request.CreateRequestDto dto =
        new ru.mirea.visitpass.dto.request.CreateRequestDto();
    dto.setVisitorFullName((String) payload.get("visitorFullName"));
    dto.setVisitorDocumentType((String) payload.get("visitorDocumentType"));
    dto.setVisitorDocumentNumber((String) payload.get("visitorDocumentNumber"));
    dto.setHostEmployeeId(
        UUID.fromString((String) payload.get("hostEmployeeId")));
    dto.setPurpose((String) payload.get("purpose"));
    dto.setVisitDate(
        LocalDate.parse((String) payload.get("visitDate")));
    dto.setTimeFrom(
        java.time.LocalTime.parse((String) payload.get("timeFrom")));
    dto.setTimeTo(
        java.time.LocalTime.parse((String) payload.get("timeTo")));
    if (payload.get("status") != null) {
      dto.setStatus((String) payload.get("status"));
    }
    if (payload.get("guestCount") != null) {
      dto.setGuestCount((Integer) payload.get("guestCount"));
    }
    if (payload.get("needsEscort") != null) {
      dto.setNeedsEscort((Boolean) payload.get("needsEscort"));
    }
    @SuppressWarnings("unchecked")
    Object rawTags = payload.get("tags");
    if (rawTags instanceof Object[] arr) {
      rawTags = java.util.List.of(arr);
    }
    if (rawTags instanceof java.util.Collection<?> coll) {
      java.util.LinkedHashSet<String> tagSet = new java.util.LinkedHashSet<>();
      for (Object t : coll) {
        tagSet.add(String.valueOf(t));
      }
      dto.setTags(tagSet);
    }
    return requestService.create(dto).getId().toString();
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void createReturns201AndFirstSequenceCode() throws Exception {
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createPayload())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestCode").value("VR-000001"))
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.guestCount").value(1))
        .andExpect(jsonPath("$.needsEscort").value(false))
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.visitor.fullName")
            .value("Fedorov Fedor Fedorovich"))
        .andExpect(jsonPath("$.host.fullName")
            .value("Ivanov Ivan Ivanovich"));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void getCardReturns200WithNestedVisitorAndHost() throws Exception {
    String uuid = createRequest();
    mockMvc.perform(get("/api/requests/{id}", uuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(uuid))
        .andExpect(jsonPath("$.requestCode").value("VR-000001"))
        .andExpect(jsonPath("$.visitor.fullName")
            .value("Fedorov Fedor Fedorovich"))
        .andExpect(jsonPath("$.visitor.documentType").value("PASSPORT"))
        .andExpect(jsonPath("$.host.fullName")
            .value("Ivanov Ivan Ivanovich"))
        .andExpect(jsonPath("$.host.department").value("Security"));
  }
  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void getMissingReturns404() throws Exception {
    mockMvc.perform(get("/api/requests/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void putWithStaleVersionReturns409() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = createPayload();
    payload.put("version", 999L);
    payload.put("purpose", "Updated purpose text");
    mockMvc.perform(put("/api/requests/{id}", uuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void putWithCurrentVersionReturns200() throws Exception {
    String uuid = createRequest();
    Map<String, Object> payload = createPayload();
    payload.put("version", 0L);
    payload.put("purpose", "Updated purpose text");
    mockMvc.perform(put("/api/requests/{id}", uuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purpose").value("Updated purpose text"));
  }

  @Test
  @WithMockUser(username = "viewer", authorities = "ROLE_VIEWER")
  void viewerCanListAndRead() throws Exception {
    String uuid = createRequestAsOperator();
    mockMvc.perform(get("/api/requests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
    mockMvc.perform(get("/api/requests/{id}", uuid))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "viewer", authorities = "ROLE_VIEWER")
  void viewerPostIsForbidden() throws Exception {
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createPayload())))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedListIs401() throws Exception {
    mockMvc.perform(get("/api/requests"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void sortHackReturns400() throws Exception {
    mockMvc.perform(get("/api/requests").param("sort", "hack"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void sizeIsCappedAt100() throws Exception {
    createRequest();
    mockMvc.perform(get("/api/requests").param("size", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void sortGuestCountDescOrders() throws Exception {
    Map<String, Object> one = createPayload();
    one.put("guestCount", 1);
    createRequest(one);
    Map<String, Object> five = createPayload();
    five.put("guestCount", 5);
    five.put("visitorFullName", "Another Visitor Name");
    createRequest(five);
    mockMvc.perform(get("/api/requests")
            .param("sort", "guestCount").param("dir", "desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].guestCount").value(5))
        .andExpect(jsonPath("$.content[1].guestCount").value(1));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void statusFilterAndArchivedModes() throws Exception {
    String active = createRequest();
    Map<String, Object> cancelled = createPayload();
    cancelled.put("status", "CANCELLED");
    cancelled.put("visitorFullName", "Cancelled Visitor Person");
    String archived = createRequest(cancelled);
    mockMvc.perform(delete("/api/requests/{id}", archived))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/requests")
            .param("status", "SUBMITTED").param("archived", "active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(active));

    mockMvc.perform(get("/api/requests")
            .param("status", "CANCELLED").param("archived", "archived"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(archived));

    mockMvc.perform(get("/api/requests").param("archived", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)));

    mockMvc.perform(get("/api/requests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void qSearchFindsByPurpose() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("purpose", "Unique zebra negotiation session");
    String uuid = createRequest(payload);
    mockMvc.perform(get("/api/requests").param("q", "zebra"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(uuid));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void qSearchFindsByRequestCode() throws Exception {
    createRequest();
    mockMvc.perform(get("/api/requests").param("q", "VR-000001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void employeeIdFilter() throws Exception {
    Employee second = new Employee();
    second.setUuid(UUID.randomUUID());
    second.setFullName("Petrova Anna Sergeevna");
    second.setDepartment("HR");
    second.setRole(EmployeeRole.HR);
    second.setActive(true);
    UUID secondId = employees.save(second).getUuid();
    Map<String, Object> payload = createPayload(secondId);
    payload.put("visitorFullName", "Second Host Visitor");
    String uuid = createRequest(payload);
    mockMvc.perform(get("/api/requests")
            .param("employeeId", secondId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(uuid));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void escortAndGuestCountFilters() throws Exception {
    Map<String, Object> big = createPayload();
    big.put("guestCount", 5);
    big.put("needsEscort", true);
    big.put("visitorFullName", "Big Escorted Group");
    String uuid = createRequest(big);
    createRequest();
    mockMvc.perform(get("/api/requests").param("escort", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(uuid));
    mockMvc.perform(get("/api/requests").param("minGuests", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
    mockMvc.perform(get("/api/requests").param("maxGuests", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void riskFilterFindsHighRisk() throws Exception {
    Map<String, Object> high = createPayload();
    high.put("guestCount", 3);
    high.put("needsEscort", true);
    high.put("visitorFullName", "High Risk Visitor");
    String uuid = createRequest(high);
    createRequest();
    mockMvc.perform(get("/api/requests").param("risk", "HIGH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(uuid))
        .andExpect(jsonPath("$.content[0].risk").value("HIGH"));
    mockMvc.perform(get("/api/requests").param("risk", "MEDIUM"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void tagFilterFindsTagged() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("tags", new String[]{"Vip", " urgent "});
    String uuid = createRequest(payload);
    createRequest();
    mockMvc.perform(get("/api/requests").param("tag", "vip"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(uuid));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void deleteArchivesAndRestoreUnarchives() throws Exception {
    String uuid = createRequest();
    mockMvc.perform(delete("/api/requests/{id}", uuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));
    mockMvc.perform(get("/api/requests/{id}", uuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));
    mockMvc.perform(post("/api/requests/{id}/restore", uuid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false));
  }

  @Test
  @WithMockUser(username = "viewer", authorities = "ROLE_VIEWER")
  void viewerDeleteIsForbidden() throws Exception {
    String uuid = createRequestAsOperator();
    mockMvc.perform(delete("/api/requests/{id}", uuid))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void permanentAsOperatorIsForbidden() throws Exception {
    String uuid = createRequest();
    mockMvc.perform(delete("/api/requests/{id}/permanent", uuid))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
  void permanentWithFlagDisabledIsForbidden() throws Exception {
    String uuid = createRequestAsOperator();
    mockMvc.perform(delete("/api/requests/{id}/permanent", uuid))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
  void permanentMissingIs404() throws Exception {
    mockMvc.perform(delete("/api/requests/{id}/permanent",
            UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void timeWindowViolationIs400() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("timeFrom", "07:00");
    payload.put("timeTo", "09:00");
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void durationViolationIs400() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("timeFrom", "10:00");
    payload.put("timeTo", "10:05");
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void issuedWithoutPassIs400() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("status", "ISSUED");
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void submittedIgnoresUnknownPassNumberProperty() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("passNumber", "PASS-123");
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.passNumber").isEmpty());
  }


  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void guestCountZeroIs400() throws Exception {
    Map<String, Object> payload = createPayload();
    payload.put("guestCount", 0);
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void disabledHostIs400() throws Exception {
    Employee host = employees.findByUuid(hostId).orElseThrow();
    host.setActive(false);
    employees.save(host);
    mockMvc.perform(post("/api/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createPayload())))
        .andExpect(status().isBadRequest());
  }

  private String createRequestAsOperator() {
    return createRequest();
  }
}
