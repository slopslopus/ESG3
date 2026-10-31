package ru.mirea.visitpass.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.DocumentType;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.Visitor;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitorRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:refdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@Transactional
class ReferenceAnalyticsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private EmployeeRepository employees;

  @Autowired
  private VisitorRepository visitors;

  @Autowired
  private VisitRequestRepository requests;

  private Employee hostA;
  private Employee hostB;
  private Employee hostC;

  private static Employee employee(String fullName, String department,
      EmployeeRole role) {
    Employee e = new Employee();
    e.setUuid(UUID.randomUUID());
    e.setFullName(fullName);
    e.setDepartment(department);
    e.setRole(role);
    e.setActive(true);
    return e;
  }

  private static Visitor visitor(String fullName) {
    Visitor v = new Visitor();
    v.setFullName(fullName);
    v.setDocumentType(DocumentType.PASSPORT);
    return v;
  }

  private static int passSeq = 100;

  private VisitRequest request(Visitor visitor, Employee host,
      VisitStatus status, LocalDate date, int guests, boolean escort,
      boolean archived) {
    VisitRequest r = new VisitRequest();
    r.setUuid(UUID.randomUUID());
    r.setRequestCode("VR-" + UUID.randomUUID().toString().substring(0, 8));
    r.setVisitor(visitor);
    r.setHost(host);
    r.setPurpose("Business meeting about deliveries");
    r.setVisitDate(date);
    r.setTimeFrom(LocalTime.of(9, 0));
    r.setTimeTo(LocalTime.of(10, 0));
    r.setStatus(status);
    if (status == VisitStatus.ISSUED || status == VisitStatus.COMPLETED) {
      r.setPassNumber("PASS-" + (passSeq++));
    }
    r.setGuestCount(guests);
    r.setNeedsEscort(escort);
    r.setArchived(archived);
    r.setCreatedAt(LocalDateTime.now());
    r.setUpdatedAt(LocalDateTime.now());
    return requests.save(r);
  }

  @BeforeEach
  void seed() {
    hostA = employees.save(employee("Alice Host", "Security", EmployeeRole.SECURITY));
    hostB = employees.save(employee("Bob Host", "IT", EmployeeRole.IT));
    hostC = employees.save(employee("Carol Host", "HR", EmployeeRole.HR));

    Visitor v1 = visitors.save(visitor("John Visitor"));
    Visitor v2 = visitors.save(visitor("Jane Visitor"));

    LocalDate today = LocalDate.now();
    // 2 COMPLETED in the past (LOW risk: past + completed)
    request(v1, hostA, VisitStatus.COMPLETED, today.minusDays(5), 1, false, false);
    request(v2, hostA, VisitStatus.COMPLETED, today.minusDays(3), 2, false, true);
    // 1 SUBMITTED future with escort+3 guests (HIGH risk)
    request(v1, hostB, VisitStatus.SUBMITTED, today.plusDays(10), 3, true, false);
  }

  @Test
  @WithMockUser(roles = "VIEWER")
  void listEmployees() throws Exception {
    mockMvc.perform(get("/api/employees"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].fullName").exists())
        .andExpect(jsonPath("$[0].department").exists())
        .andExpect(jsonPath("$[0].role").exists())
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  void createAsOperatorIsForbidden() throws Exception {
    mockMvc.perform(post("/api/employees")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Dave New","department":"Finance","role":"FINANCE"}"""))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void createAsAdmin() throws Exception {
    mockMvc.perform(post("/api/employees")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Dave New","department":"Finance","role":"FINANCE"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fullName").value("Dave New"))
        .andExpect(jsonPath("$.department").value("Finance"))
        .andExpect(jsonPath("$.role").value("FINANCE"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void updateAsAdmin() throws Exception {
    mockMvc.perform(put("/api/employees/" + hostA.getUuid())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"Alice Updated","department":"Sales","role":"SALES"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Alice Updated"))
        .andExpect(jsonPath("$.department").value("Sales"))
        .andExpect(jsonPath("$.role").value("SALES"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void disableHidesFromActiveFilter() throws Exception {
    mockMvc.perform(post("/api/employees/" + hostC.getUuid() + "/disable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc.perform(get("/api/employees?active=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));

    mockMvc.perform(post("/api/employees/" + hostC.getUuid() + "/enable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    mockMvc.perform(get("/api/employees?active=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
  }

  @Test
  @WithMockUser(roles = "VIEWER")
  void analyticsSummary() throws Exception {
    // Controlled seed: total=3, archived=1 (COMPLETED past, inactive),
    // active=2: 1 COMPLETED past (LOW) + 1 SUBMITTED escort+3 (HIGH).
    // escortCount=1, avgGuestCount=(1+3)/2=2.0,
    // byStatus={COMPLETED:1,SUBMITTED:1}, byRisk={LOW:1,HIGH:1}.
    mockMvc.perform(get("/api/analytics/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.active").value(2))
        .andExpect(jsonPath("$.archived").value(1))
        .andExpect(jsonPath("$.escortCount").value(1))
        .andExpect(jsonPath("$.avgGuestCount", closeTo(2.0, 0.001)))
        .andExpect(jsonPath("$.byStatus.COMPLETED").value(1))
        .andExpect(jsonPath("$.byStatus.SUBMITTED").value(1))
        .andExpect(jsonPath("$.byRisk.LOW").value(1))
        .andExpect(jsonPath("$.byRisk.HIGH").value(1))
        .andExpect(jsonPath("$.byEmployee", hasSize(2)))
        .andExpect(jsonPath("$.byEmployee[*].employeeId",
            containsInAnyOrder(hostA.getUuid().toString(),
                hostB.getUuid().toString())))
        .andExpect(jsonPath("$.byEmployee[0].employeeName").exists())
        .andExpect(jsonPath("$.byEmployee[0].count").value(1));
  }
}
