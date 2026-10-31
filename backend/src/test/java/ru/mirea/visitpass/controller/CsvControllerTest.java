package ru.mirea.visitpass.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.repository.AuditEventRepository;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;
import ru.mirea.visitpass.repository.VisitorRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:csvdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class CsvControllerTest {

  private static final String HEADER = "requestCode,visitorFullName,"
      + "visitorDocumentType,hostEmployeeId,purpose,visitDate,timeFrom,"
      + "timeTo,status,guestCount,needsEscort,tags";

  @Autowired
  private MockMvc mockMvc;

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
  private AuditEventRepository audits;

  @Autowired
  private ru.mirea.visitpass.service.RequestService requestService;

  private UUID hostId;

  @BeforeEach
  void setUp() {
    requests.deleteAll();
    tags.deleteAll();
    visitors.deleteAll();
    employees.deleteAll();
    audits.deleteAll();
    jdbcTemplate.execute(
        "CREATE SEQUENCE IF NOT EXISTS visit_request_code_seq START WITH 1");
    jdbcTemplate.execute(
        "ALTER SEQUENCE visit_request_code_seq RESTART WITH 1");
    jdbcTemplate.execute(
        "CREATE SEQUENCE IF NOT EXISTS pass_number_seq START WITH 1");
    jdbcTemplate.execute("ALTER SEQUENCE pass_number_seq RESTART WITH 1");
    Employee host = new Employee();
    host.setUuid(UUID.randomUUID());
    host.setFullName("Ivanov Ivan Ivanovich");
    host.setDepartment("Security");
    host.setRole(EmployeeRole.SECURITY);
    host.setActive(true);
    hostId = employees.save(host).getUuid();
  }

  private void createRequest(String purpose, String status) {
    ru.mirea.visitpass.dto.request.CreateRequestDto dto =
        new ru.mirea.visitpass.dto.request.CreateRequestDto();
    dto.setVisitorFullName("Petrov Petr Petrovich");
    dto.setVisitorDocumentType("PASSPORT");
    dto.setVisitorDocumentNumber("1111 222222");
    dto.setHostEmployeeId(hostId);
    dto.setPurpose(purpose);
    dto.setVisitDate(LocalDate.now().plusDays(3));
    dto.setTimeFrom(java.time.LocalTime.of(10, 0));
    dto.setTimeTo(java.time.LocalTime.of(11, 0));
    dto.setStatus(status);
    requestService.create(dto);
  }

  private String csvRow(String host, String status) {
    return ",Fedorov Fedor Fedorovich,PASSPORT," + host
        + ",Contract signing meeting," + LocalDate.now().plusDays(3)
        + ",10:00,11:00," + status + ",2,true,vip;internal";
  }

  @Test
  @WithMockUser(username = "v", authorities = "ROLE_VIEWER")
  void exportReturnsCsvWithHeader() throws Exception {
    createRequest("Contract signing meeting", "SUBMITTED");
    mockMvc.perform(get("/api/requests/export"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(header().string("Content-Disposition",
            "attachment; filename=\"requests.csv\""))
        .andExpect(content().string(org.hamcrest.Matchers
            .startsWith(HEADER + "\n")));
  }

  @Test
  @WithMockUser(username = "v", authorities = "ROLE_VIEWER")
  void exportFilterStatusReallyFilters() throws Exception {
    createRequest("Contract signing meeting", "SUBMITTED");
    createRequest("Cancelled visit purpose", "CANCELLED");
    String body = mockMvc.perform(
            get("/api/requests/export").param("status", "SUBMITTED"))
        .andExpect(status().isOk())
        .andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
    String[] lines = body.strip().split("\n");
    assertThat(lines).hasSize(2);
    assertThat(lines[1]).contains("SUBMITTED");
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewValidRowsWritesNothing() throws Exception {
    long before = requests.count();
    String csv = HEADER + "\n"
        + csvRow(hostId.toString(), "SUBMITTED") + "\n"
        + csvRow(hostId.toString(), "CANCELLED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(2))
        .andExpect(jsonPath("$.errors").isEmpty());
    assertThat(requests.count()).isEqualTo(before);
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewBadHostUuidReturnsRowErrors() throws Exception {
    String csv = HEADER + "\n"
        + csvRow("not-a-uuid", "SUBMITTED") + "\n"
        + csvRow(UUID.randomUUID().toString(), "SUBMITTED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1))
        .andExpect(jsonPath("$.errors[0].field")
            .value("hostEmployeeId"))
        .andExpect(jsonPath("$.errors[1].row").value(2));
    assertThat(requests.count()).isEqualTo(0);
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewBadStatusReturnsRowError() throws Exception {
    String csv = HEADER + "\n"
        + csvRow(hostId.toString(), "BOGUS") + "\n";
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("status"));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void applyCreateOnlyDuplicateCodeIsRowError() throws Exception {
    createRequest("Contract signing meeting", "SUBMITTED");
    String code = requests.findAll().get(0).getRequestCode();
    String csv = HEADER + "\n"
        + code + csvRow(hostId.toString(), "SUBMITTED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "CREATE_ONLY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void applyCreateOnlyNewCreates() throws Exception {
    String csv = HEADER + "\n"
        + csvRow(hostId.toString(), "SUBMITTED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "CREATE_ONLY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1));
    assertThat(requests.count()).isEqualTo(1);
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void applyUpsertUpdatesExisting() throws Exception {
    createRequest("Contract signing meeting", "SUBMITTED");
    String code = requests.findAll().get(0).getRequestCode();
    String csv = HEADER + "\n"
        + code + csvRow(hostId.toString(), "CANCELLED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "UPSERT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated").value(1));
    assertThat(requests.findAll().get(0).getStatus().name())
        .isEqualTo("CANCELLED");
  }

  @Test
  @WithMockUser(username = "v", authorities = "ROLE_VIEWER")
  void viewerApplyIsForbidden() throws Exception {
    String csv = HEADER + "\n"
        + csvRow(hostId.toString(), "SUBMITTED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "CREATE_ONLY"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void applyWritesImportAppliedAudit() throws Exception {
    String csv = HEADER + "\n"
        + csvRow(hostId.toString(), "SUBMITTED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "CREATE_ONLY"))
        .andExpect(status().isOk());
    assertThat(audits.count()).isEqualTo(1);
    assertThat(audits.findAll().get(0).getEventType())
        .isEqualTo("IMPORT_APPLIED");
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewEmptyFileReturns400() throws Exception {
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", new byte[0])))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("CSV file is empty"));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewHeaderOnlyFileReturns400() throws Exception {
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv",
                (HEADER + "\n").getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("CSV file is empty"));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewShortRowIsRowErrorNot500() throws Exception {
    String csv = HEADER + "\n"
        + ",Short Name,PASSPORT," + hostId
        + ",Contract signing meeting,"
        + LocalDate.now().plusDays(3) + ",10:00,11:00,SUBMITTED,1\n";
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("needsEscort"));
  }
  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewEmptyNeedsEscortCellIsRowError() throws Exception {
    String csv = HEADER + "\n"
        + ",Short Name,PASSPORT," + hostId
        + ",Contract signing meeting,"
        + LocalDate.now().plusDays(3)
        + ",10:00,11:00,SUBMITTED,1,,\n";
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("needsEscort"));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void previewMissingRequestCodeIsRowError() throws Exception {
    String csv = HEADER + "\n"
        + ",,,,,,,,,,,\n";
    mockMvc.perform(multipart("/api/requests/import/preview")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validCount").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void applyUpsertMissingRequestCodeIsRowError() throws Exception {
    String csv = HEADER + "\n"
        + ",,,,,,,,,,,\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "UPSERT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.updated").value(0))
        .andExpect(jsonPath("$.errors[0].row").value(1));
  }

  @Test
  @WithMockUser(username = "op", authorities = "ROLE_OPERATOR")
  void applyUnknownModeReturns400() throws Exception {
    String csv = HEADER + "\n"
        + csvRow(hostId.toString(), "SUBMITTED") + "\n";
    mockMvc.perform(multipart("/api/requests/import/apply")
            .file(new MockMultipartFile("file", "import.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("mode", "BOGUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Unknown mode: BOGUS"));
  }

  @Test
  void unauthenticatedExportIs401() throws Exception {
    mockMvc.perform(get("/api/requests/export"))
        .andExpect(status().isUnauthorized());
  }
  }


