package ru.mirea.visitpass.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.config.AppProperties;
import ru.mirea.visitpass.domain.DocumentType;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitRisk;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.VisitTag;
import ru.mirea.visitpass.domain.Visitor;
import ru.mirea.visitpass.dto.request.CreateRequestDto;
import ru.mirea.visitpass.dto.request.RequestResponse;
import ru.mirea.visitpass.dto.request.UpdateRequestDto;
import ru.mirea.visitpass.error.ForbiddenException;
import ru.mirea.visitpass.error.ResourceNotFoundException;
import ru.mirea.visitpass.error.VersionConflictException;
import ru.mirea.visitpass.filter.RequestFilter;
import ru.mirea.visitpass.filter.RequestSpecifications;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;
import ru.mirea.visitpass.repository.VisitorRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RequestService {

  private static final LocalTime OPEN_FROM = LocalTime.of(8, 0);
  private static final LocalTime OPEN_TO = LocalTime.of(20, 0);
  private static final Duration MIN_DURATION = Duration.ofMinutes(15);
  private static final Duration MAX_DURATION = Duration.ofHours(12);

  private final VisitRequestRepository requests;
  private final EmployeeRepository employees;
  private final VisitorRepository visitors;
  private final VisitTagRepository tags;
  private final VisitRiskCalculator risks;
  private final AppProperties properties;

  @PersistenceContext
  private EntityManager entityManager;

  public RequestService(VisitRequestRepository requests,
      EmployeeRepository employees, VisitorRepository visitors,
      VisitTagRepository tags, VisitRiskCalculator risks,
      AppProperties properties) {
    this.requests = requests;
    this.employees = employees;
    this.visitors = visitors;
    this.tags = tags;
    this.risks = risks;
    this.properties = properties;
  }

  /**
   * Lists requests. When {@code risk} is set, totals are page-relative
   * (risk is computed in memory, not stored).
   */
  @Transactional(readOnly = true)
  public Page<RequestResponse> list(RequestFilter filter, Pageable pageable) {
    Page<VisitRequest> page =
        requests.findAll(RequestSpecifications.byFilter(filter), pageable);
    List<RequestResponse> content = page.getContent().stream()
        .map(this::toResponse)
        .toList();
    if (filter.getRisk() != null && !filter.getRisk().isBlank()) {
      VisitRisk wanted;
      try {
        wanted = VisitRisk.valueOf(
            filter.getRisk().trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException(
            "Unknown risk: " + filter.getRisk());
      }
      content = content.stream()
          .filter(r -> wanted.name().equals(r.getRisk()))
          .toList();
      return new PageImpl<>(content, pageable, content.size());
    }
    return new PageImpl<>(content, pageable, page.getTotalElements());
  }

  @Transactional(readOnly = true)
  public RequestResponse get(UUID uuid) {
    return toResponse(findByUuid(uuid));
  }

  /**
   * Creates a request. {@code tags == null} means "no tags" (empty set).
   * passNumber is never accepted from clients — it is assigned
   * server-side from {@code pass_number_seq} when the resulting status
   * requires it (ISSUED/COMPLETED).
   */
  @Transactional
  public RequestResponse create(CreateRequestDto dto) {
    VisitStatus status = parseStatus(dto.getStatus(), VisitStatus.SUBMITTED);
    Employee host = findActiveHost(dto.getHostEmployeeId());
    checkTimeWindow(dto.getTimeFrom(), dto.getTimeTo());
    String passNumber = requiresPass(status) ? nextPassNumber() : null;
    VisitRequest entity = new VisitRequest();
    entity.setUuid(UUID.randomUUID());
    entity.setRequestCode(nextRequestCode());
    Visitor visitor = new Visitor();
    visitor.setFullName(dto.getVisitorFullName().trim());
    visitor.setDocumentType(parseDocumentType(dto.getVisitorDocumentType()));
    visitor.setDocumentNumber(dto.getVisitorDocumentNumber().trim());
    entity.setVisitor(visitors.save(visitor));
    entity.setHost(host);
    entity.setPurpose(dto.getPurpose().trim());
    entity.setVisitDate(dto.getVisitDate());
    entity.setTimeFrom(dto.getTimeFrom());
    entity.setTimeTo(dto.getTimeTo());
    entity.setStatus(status);
    entity.setPassNumber(passNumber);
    entity.setGuestCount(
        dto.getGuestCount() == null ? 1 : dto.getGuestCount());
    entity.setNeedsEscort(Boolean.TRUE.equals(dto.getNeedsEscort()));
    entity.setArchived(false);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setTags(resolveTags(dto.getTags()));
    VisitRequest saved = requests.save(entity);
    // TODO(audit): record(REQUEST_CREATED, VISIT_REQUEST, uuid, ...)
    return toResponse(saved);
  }

  /**
   * Updates a request. {@code tags == null} leaves tags unchanged
   * (PUT without tags must not wipe them); a non-null set replaces them.
   * passNumber is never accepted from clients — it is assigned
   * server-side from {@code pass_number_seq} on first transition to
   * ISSUED/COMPLETED.
   */
  @Transactional
  public RequestResponse update(UUID uuid, UpdateRequestDto dto) {
    VisitRequest entity = findByUuid(uuid);
    if (!entity.getVersion().equals(dto.getVersion())) {
      throw new VersionConflictException(
          "Stale version: expected " + entity.getVersion()
              + " but was " + dto.getVersion());
    }
    VisitStatus status = parseStatus(dto.getStatus(), entity.getStatus());
    Employee host = findActiveHost(dto.getHostEmployeeId());
    checkTimeWindow(dto.getTimeFrom(), dto.getTimeTo());
    if (requiresPass(status)
        && (entity.getPassNumber() == null
            || entity.getPassNumber().isBlank())) {
      entity.setPassNumber(nextPassNumber());
    }
    checkPassNumber(status, entity.getPassNumber());
    entity.getVisitor().setFullName(dto.getVisitorFullName().trim());
    entity.getVisitor()
        .setDocumentType(parseDocumentType(dto.getVisitorDocumentType()));
    entity.getVisitor()
        .setDocumentNumber(dto.getVisitorDocumentNumber().trim());
    entity.setHost(host);
    entity.setPurpose(dto.getPurpose().trim());
    entity.setVisitDate(dto.getVisitDate());
    entity.setTimeFrom(dto.getTimeFrom());
    entity.setTimeTo(dto.getTimeTo());
    entity.setStatus(status);
    if (dto.getGuestCount() != null) {
      entity.setGuestCount(dto.getGuestCount());
    }
    if (dto.getNeedsEscort() != null) {
      entity.setNeedsEscort(dto.getNeedsEscort());
    }
    if (dto.getTags() != null) {
      entity.setTags(resolveTags(dto.getTags()));
    }
    entity.setUpdatedAt(LocalDateTime.now());
    VisitRequest saved = requests.save(entity);
    // TODO(audit): record(REQUEST_UPDATED, VISIT_REQUEST, uuid, ...)
    return toResponse(saved);
  }

  @Transactional
  public RequestResponse archive(UUID uuid) {
    VisitRequest entity = findByUuid(uuid);
    entity.setArchived(true);
    entity.setUpdatedAt(LocalDateTime.now());
    VisitRequest saved = requests.save(entity);
    // TODO(audit): record(REQUEST_ARCHIVED, VISIT_REQUEST, uuid, ...)
    return toResponse(saved);
  }

  @Transactional
  public RequestResponse restore(UUID uuid) {
    VisitRequest entity = findByUuid(uuid);
    entity.setArchived(false);
    entity.setUpdatedAt(LocalDateTime.now());
    VisitRequest saved = requests.save(entity);
    // TODO(audit): record(REQUEST_RESTORED, VISIT_REQUEST, uuid, ...)
    return toResponse(saved);
  }

  @Transactional
  public void deletePermanent(UUID uuid) {
    VisitRequest entity = requests.findByUuid(uuid)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Request not found: " + uuid));
    if (!properties.getSecurity().isPermanentDeleteEnabled()) {
      throw new ForbiddenException("Permanent delete is disabled");
    }
    requests.delete(entity);
    // TODO(audit): record(REQUEST_DELETED, VISIT_REQUEST, uuid, ...)
  }


  private VisitRequest findByUuid(UUID uuid) {
    return requests.findByUuid(uuid)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Request not found: " + uuid));
  }

  private Employee findActiveHost(UUID hostId) {
    Employee host = employees.findByUuid(hostId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Host employee not found: " + hostId));
    if (!host.isActive()) {
      throw new IllegalArgumentException(
          "Host employee is disabled: " + hostId);
    }
    return host;
  }

  private String nextRequestCode() {
    Number next = (Number) entityManager
        .createNativeQuery("SELECT nextval('visit_request_code_seq')")
        .getSingleResult();
    return "VR-" + String.format("%06d", next.longValue());
  }

  private String nextPassNumber() {
    Number next = (Number) entityManager
        .createNativeQuery("SELECT nextval('pass_number_seq')")
        .getSingleResult();
    return "PASS-" + String.format("%03d", next.longValue());
  }

  private static boolean requiresPass(VisitStatus status) {
    return status == VisitStatus.ISSUED || status == VisitStatus.COMPLETED;
  }

  private VisitStatus parseStatus(String raw, VisitStatus fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return VisitStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown status: " + raw);
    }
  }

  private DocumentType parseDocumentType(String raw) {
    try {
      return DocumentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new IllegalArgumentException(
          "Unknown document type: " + raw);
    }
  }

  private void checkTimeWindow(java.time.LocalTime from,
      java.time.LocalTime to) {
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("time_from must be before time_to");
    }
    if (from.isBefore(OPEN_FROM) || to.isAfter(OPEN_TO)) {
      throw new IllegalArgumentException(
          "Visit must be within 08:00-20:00");
    }
    Duration duration = Duration.between(from, to);
    if (duration.compareTo(MIN_DURATION) < 0
        || duration.compareTo(MAX_DURATION) > 0) {
      throw new IllegalArgumentException(
          "Visit duration must be 15 minutes to 12 hours");
    }
  }

  private void checkPassNumber(VisitStatus status, String existing) {
    if ((status == VisitStatus.ISSUED
        || status == VisitStatus.COMPLETED)
        && (existing == null || !existing.matches("^PASS-[0-9]{3,6}$"))) {
      throw new IllegalArgumentException(
          "passNumber PASS-NNN is required for ISSUED/COMPLETED requests");
    }
    if ((status == VisitStatus.SUBMITTED
        || status == VisitStatus.CANCELLED) && existing != null) {
      throw new IllegalArgumentException(
          "passNumber must be absent for SUBMITTED/CANCELLED requests");
    }
  }

  private Set<VisitTag> resolveTags(Set<String> raw) {
    if (raw == null) {
      return new LinkedHashSet<>();
    }
    Set<VisitTag> result = new LinkedHashSet<>();
    for (String name : raw) {
      if (name == null) {
        continue;
      }
      String normalized = name.trim().toLowerCase(Locale.ROOT);
      if (normalized.isEmpty()) {
        continue;
      }
      VisitTag tag = tags.findByName(normalized).orElseGet(() -> {
        VisitTag created = new VisitTag();
        created.setName(normalized);
        return tags.save(created);
      });
      result.add(tag);
    }
    return result;
  }

  RequestResponse toResponse(VisitRequest entity) {
    RequestResponse response = new RequestResponse();
    response.setId(entity.getUuid());
    response.setRequestCode(entity.getRequestCode());
    response.setVersion(
        entity.getVersion() == null ? 0L : entity.getVersion());
    response.setVisitor(new RequestResponse.VisitorPart(
        entity.getVisitor().getFullName(),
        entity.getVisitor().getDocumentType().name(),
        entity.getVisitor().getDocumentNumber()));
    response.setHost(new RequestResponse.HostPart(
        entity.getHost().getUuid(),
        entity.getHost().getFullName(),
        entity.getHost().getDepartment()));
    response.setPurpose(entity.getPurpose());
    response.setVisitDate(entity.getVisitDate());
    response.setTimeFrom(entity.getTimeFrom());
    response.setTimeTo(entity.getTimeTo());
    response.setStatus(entity.getStatus().name());
    response.setPassNumber(entity.getPassNumber());
    response.setArrivedAt(entity.getArrivedAt());
    response.setLeftAt(entity.getLeftAt());
    response.setGuestCount(entity.getGuestCount());
    response.setNeedsEscort(entity.isNeedsEscort());
    response.setRisk(risks.calculate(entity).name());
    response.setArchived(entity.isArchived());
    response.setTags(entity.getTags() == null ? Set.of()
        : entity.getTags().stream()
            .map(VisitTag::getName)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    response.setCreatedAt(entity.getCreatedAt());
    response.setUpdatedAt(entity.getUpdatedAt());
    return response;
  }
}
