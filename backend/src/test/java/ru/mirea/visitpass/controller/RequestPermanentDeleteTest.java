package ru.mirea.visitpass.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.dto.request.CreateRequestDto;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.service.RequestService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:requestsdb-perm;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "app.security.permanent-delete-enabled=true"
})
class RequestPermanentDeleteTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EmployeeRepository employees;

  @Autowired
  private RequestService requestService;

  @Test
  @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
  void permanentWithFlagOnDeletes() throws Exception {
    jdbcTemplate.execute(
        "CREATE SEQUENCE IF NOT EXISTS visit_request_code_seq"
            + " START WITH 1");
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
    UUID hostUuid = employees.save(host).getUuid();
    CreateRequestDto dto = new CreateRequestDto();
    dto.setVisitorFullName("Fedorov Fedor Fedorovich");
    dto.setVisitorDocumentType("PASSPORT");
    dto.setVisitorDocumentNumber("1234 567890");
    dto.setHostEmployeeId(hostUuid);
    dto.setPurpose("Contract signing meeting");
    dto.setVisitDate(LocalDate.now().plusDays(3));
    dto.setTimeFrom(LocalTime.of(10, 0));
    dto.setTimeTo(LocalTime.of(11, 0));
    String uuid = requestService.create(dto).getId().toString();
    mockMvc.perform(delete("/api/requests/{id}/permanent", uuid))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/requests/{id}", uuid))
        .andExpect(status().isNotFound());
  }
}
