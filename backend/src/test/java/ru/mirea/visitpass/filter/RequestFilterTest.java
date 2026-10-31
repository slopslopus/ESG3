package ru.mirea.visitpass.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import ru.mirea.visitpass.domain.DocumentType;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.VisitTag;
import ru.mirea.visitpass.domain.Visitor;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;
import ru.mirea.visitpass.repository.VisitorRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:filterdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class RequestFilterTest {

  @Autowired
  private VisitRequestRepository requests;

  @Autowired
  private EmployeeRepository employees;

  @Autowired
  private VisitorRepository visitors;

  @Autowired
  private VisitTagRepository tags;

  private VisitRequest submitted;
  private VisitRequest issued;
  private VisitRequest archivedCancelled;

  @BeforeEach
  void setUp() {
    requests.deleteAll();
    tags.deleteAll();
    visitors.deleteAll();
    employees.deleteAll();

    Employee host = new Employee();
    host.setUuid(UUID.randomUUID());
    host.setFullName("Petrov Petr Petrovich");
    host.setDepartment("Security");
    host.setRole(EmployeeRole.SECURITY);
    host.setActive(true);
    host = employees.save(host);

    VisitTag vip = new VisitTag();
    vip.setName("vip");
    vip = tags.save(vip);

    submitted = saveRequest("Sidorov Sidr Sidorovich",
        "Contract signing meeting", "VR-000101", VisitStatus.SUBMITTED,
        false, host, Set.of(vip));
    issued = saveRequest("Kuznetsov Kuzma Kuzmich",
        "Equipment maintenance", "VR-000102", VisitStatus.ISSUED,
        false, host, Set.of());
    archivedCancelled = saveRequest("Smirnov Smir Smirnovich",
        "Old archive visit", "VR-000103", VisitStatus.CANCELLED,
        true, host, Set.of());
  }

  private VisitRequest saveRequest(String visitorName, String purpose,
      String code, VisitStatus status, boolean archived, Employee host,
      Set<VisitTag> requestTags) {
    Visitor visitor = new Visitor();
    visitor.setFullName(visitorName);
    visitor.setDocumentType(DocumentType.PASSPORT);
    visitor.setDocumentNumber("1234 567890");
    visitor = visitors.save(visitor);

    VisitRequest request = new VisitRequest();
    request.setUuid(UUID.randomUUID());
    request.setRequestCode(code);
    request.setVisitor(visitor);
    request.setHost(host);
    request.setPurpose(purpose);
    request.setVisitDate(LocalDate.now().plusDays(3));
    request.setTimeFrom(LocalTime.of(10, 0));
    request.setTimeTo(LocalTime.of(11, 0));
    request.setStatus(status);
    request.setGuestCount(1);
    request.setNeedsEscort(false);
    request.setArchived(archived);
    request.setCreatedAt(LocalDateTime.now());
    request.setUpdatedAt(LocalDateTime.now());
    request.setTags(requestTags);
    return requests.save(request);
  }

  private RequestFilter filter() {
    RequestFilter filter = new RequestFilter();
    filter.setArchived("all");
    return filter;
  }

  private List<String> codes(RequestFilter filter) {
    return requests.findAll(RequestSpecifications.byFilter(filter))
        .stream().map(VisitRequest::getRequestCode).sorted().toList();
  }

  @Test
  void qFindsByPurpose() {
    RequestFilter filter = filter();
    filter.setQ("contract signing");
    assertEquals(List.of("VR-000101"), codes(filter));
  }

  @Test
  void qFindsByCodeCaseInsensitive() {
    RequestFilter filter = filter();
    filter.setQ("vr-000102");
    assertEquals(List.of("VR-000102"), codes(filter));
  }

  @Test
  void statusFilters() {
    RequestFilter filter = filter();
    filter.setStatus("submitted");
    assertEquals(List.of("VR-000101"), codes(filter));
  }

  @Test
  void tagFiltersCaseAndSpaceInsensitive() {
    RequestFilter filter = filter();
    filter.setTag("  VIP  ");
    assertEquals(List.of("VR-000101"), codes(filter));
  }

  @Test
  void archivedAllReturnsEverythingWhileActiveExcludesArchived() {
    RequestFilter all = filter();
    all.setArchived("all");
    List<String> allCodes = codes(all);
    assertEquals(3, allCodes.size());
    assertTrue(allCodes.contains(archivedCancelled.getRequestCode()));

    RequestFilter active = filter();
    active.setArchived("active");
    List<String> activeCodes = codes(active);
    assertEquals(2, activeCodes.size());
    assertTrue(activeCodes.contains(submitted.getRequestCode()));
    assertTrue(activeCodes.contains(issued.getRequestCode()));
  }
}
