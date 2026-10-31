package ru.mirea.visitpass.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.DocumentType;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.VisitTag;
import ru.mirea.visitpass.domain.Visitor;
import ru.mirea.visitpass.filter.RequestFilter;
import ru.mirea.visitpass.filter.RequestSpecifications;
import ru.mirea.visitpass.repository.EmployeeRepository;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;
import ru.mirea.visitpass.repository.VisitorRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CsvService {

  private static final List<String> HEADERS = List.of(
      "requestCode", "visitorFullName", "visitorDocumentType",
      "hostEmployeeId", "purpose", "visitDate", "timeFrom", "timeTo",
      "status", "guestCount", "needsEscort", "tags");

  private static final LocalTime OPEN_FROM = LocalTime.of(8, 0);

  private static final LocalTime OPEN_TO = LocalTime.of(20, 0);

  private static final Duration MIN_DURATION = Duration.ofMinutes(15);

  private static final Duration MAX_DURATION = Duration.ofHours(12);

  public record CsvRowError(int row, String field, String message) {
  }

  public record PreviewResult(int validCount, List<CsvRowError> errors) {
  }

  public record ApplyResult(int created, int updated,
      List<CsvRowError> errors) {
  }

  public enum ImportMode {
    CREATE_ONLY,
    UPSERT
  }

  private final VisitRequestRepository requests;

  private final EmployeeRepository employees;

  private final VisitorRepository visitors;

  private final VisitTagRepository tags;

  private final AuditService audits;

  @PersistenceContext
  private EntityManager entityManager;

  public CsvService(VisitRequestRepository requests,
      EmployeeRepository employees, VisitorRepository visitors,
      VisitTagRepository tags, AuditService audits) {
    this.requests = requests;
    this.employees = employees;
    this.visitors = visitors;
    this.tags = tags;
    this.audits = audits;
  }
  @Transactional(readOnly = true)
  public byte[] exportCsv(RequestFilter filter) {
    List<VisitRequest> found = requests.findAll(
        RequestSpecifications.byFilter(filter),
        Sort.by(Sort.Direction.ASC, "visitDate"));
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer =
            new OutputStreamWriter(out, StandardCharsets.UTF_8);
        CSVPrinter printer = new CSVPrinter(writer,
            CSVFormat.DEFAULT.builder().setHeader(
                HEADERS.toArray(new String[0]))
                .setRecordSeparator("\n").get())) {
      for (VisitRequest r : found) {
        java.time.format.DateTimeFormatter timeFormat =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        printer.printRecord(
            r.getRequestCode(),
            r.getVisitor().getFullName(),
            r.getVisitor().getDocumentType().name(),
            r.getHost().getUuid().toString(),
            r.getPurpose(),
            r.getVisitDate().toString(),
            r.getTimeFrom().format(timeFormat),
            r.getTimeTo().format(timeFormat),
            r.getStatus().name(),
            String.valueOf(r.getGuestCount()),
            String.valueOf(r.isNeedsEscort()),
            r.getTags() == null ? "" : r.getTags().stream()
                .map(VisitTag::getName)
                .sorted()
                .collect(Collectors.joining(";")));
      }
      printer.flush();
      writer.flush();
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("CSV export failed", ex);
    }
  }

  @Transactional(readOnly = true)
  public PreviewResult preview(InputStream input) {
    List<CSVRecord> records = parse(input);
    List<CsvRowError> errors = new ArrayList<>();
    int valid = 0;
    for (int i = 0; i < records.size(); i++) {
      CsvRowError error = validate(records.get(i), i + 1, null);
      if (error == null) {
        valid++;
      } else {
        errors.add(error);
      }
    }
    return new PreviewResult(valid, errors);
  }

  @Transactional
  public ApplyResult apply(InputStream input, ImportMode mode) {
    List<CSVRecord> records = parse(input);
    List<CsvRowError> errors = new ArrayList<>();
    int created = 0;
    int updated = 0;
    for (int i = 0; i < records.size(); i++) {
      int row = i + 1;
      CSVRecord record = records.get(i);
      String rawCode = record.isMapped("requestCode")
          ? record.get("requestCode") : null;
      String code = rawCode == null ? "" : rawCode.trim();
      VisitRequest existing = mode == ImportMode.UPSERT && !code.isEmpty()
          ? findByRequestCode(code) : null;
      CsvRowError error = validate(record, row,
          mode == ImportMode.UPSERT && !code.isEmpty() ? code : null);
      if (error != null) {
        errors.add(error);
        continue;
      }
      if (mode == ImportMode.CREATE_ONLY) {
        persistCreate(record);
        created++;
      } else if (existing != null) {
        persistUpdate(existing, record);
        updated++;
      } else {
        persistCreate(record);
        created++;
      }
    }
    audits.record("IMPORT_APPLIED", "VISIT_REQUEST", null,
        "CSV import applied: created=" + created
            + " updated=" + updated);
    return new ApplyResult(created, updated, errors);
  }

  private List<CSVRecord> parse(InputStream input) {
    try {
      String text =
          new String(input.readAllBytes(), StandardCharsets.UTF_8);
      if (!text.isEmpty() && text.charAt(0) == '﻿') {
        text = text.substring(1);
      }
      if (text.isBlank()) {
        throw new IllegalArgumentException("CSV file is empty");
      }
      CSVFormat format = CSVFormat.DEFAULT.builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setTrim(true)
          .setIgnoreEmptyLines(true)
          .get();
      try (CSVParser parser = new CSVParser(new StringReader(text),
          format)) {
        List<CSVRecord> records = parser.getRecords();
        if (records.isEmpty()) {
          throw new IllegalArgumentException("CSV file is empty");
        }
        for (String header : HEADERS) {
          if (!parser.getHeaderMap().containsKey(header)) {
            throw new IllegalArgumentException(
                "Missing CSV column: " + header);
          }
        }
        return records;
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException(
          "Cannot read CSV file: " + ex.getMessage());
    }
  }

  private CsvRowError validate(CSVRecord record, int row,
      String upsertCode) {
    if (record.size() < HEADERS.size()) {
      String missing = HEADERS.get(record.size());
      return new CsvRowError(row, missing,
          "Missing value for column: " + missing);
    }
    String fullName = record.get("visitorFullName");
    if (fullName == null || fullName.isBlank()
        || fullName.trim().length() < 2
        || fullName.trim().length() > 150) {
      return new CsvRowError(row, "visitorFullName",
          "Full name must be 2-150 characters");
    }
    try {
      DocumentType.valueOf(record.get("visitorDocumentType").trim()
          .toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException ex) {
      return new CsvRowError(row, "visitorDocumentType",
          "Unknown document type: "
              + record.get("visitorDocumentType"));
    }
    String hostRaw = record.get("hostEmployeeId");
    UUID hostId;
    try {
      hostId = UUID.fromString(hostRaw.trim());
    } catch (IllegalArgumentException | NullPointerException ex) {
      return new CsvRowError(row, "hostEmployeeId",
          "Invalid employee UUID: " + hostRaw);
    }
    Employee host = employees.findByUuid(hostId).orElse(null);
    if (host == null) {
      return new CsvRowError(row, "hostEmployeeId",
          "Host employee not found: " + hostRaw);
    }
    if (!host.isActive()) {
      return new CsvRowError(row, "hostEmployeeId",
          "Host employee is disabled: " + hostRaw);
    }
    String purpose = record.get("purpose");
    if (purpose == null || purpose.isBlank()
        || purpose.trim().length() < 5
        || purpose.trim().length() > 1000) {
      return new CsvRowError(row, "purpose",
          "Purpose must be 5-1000 characters");
    }
    LocalDate visitDate;
    try {
      visitDate = LocalDate.parse(record.get("visitDate").trim());
    } catch (DateTimeParseException | NullPointerException ex) {
      return new CsvRowError(row, "visitDate",
          "Invalid date (expected yyyy-MM-dd): "
              + record.get("visitDate"));
    }
    LocalTime from;
    LocalTime to;
    try {
      from = LocalTime.parse(record.get("timeFrom").trim());
      to = LocalTime.parse(record.get("timeTo").trim());
    } catch (DateTimeParseException | NullPointerException ex) {
      return new CsvRowError(row, "timeFrom",
          "Invalid time (expected HH:mm)");
    }
    if (!from.isBefore(to)) {
      return new CsvRowError(row, "timeFrom",
          "time_from must be before time_to");
    }
    if (from.isBefore(OPEN_FROM) || to.isAfter(OPEN_TO)) {
      return new CsvRowError(row, "timeFrom",
          "Visit must be within 08:00-20:00");
    }
    Duration duration = Duration.between(from, to);
    if (duration.compareTo(MIN_DURATION) < 0
        || duration.compareTo(MAX_DURATION) > 0) {
      return new CsvRowError(row, "timeFrom",
          "Visit duration must be 15 minutes to 12 hours");
    }
    try {
      VisitStatus.valueOf(record.get("status").trim()
          .toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException ex) {
      return new CsvRowError(row, "status",
          "Unknown status: " + record.get("status"));
    }
    int guestCount;
    try {
      guestCount = Integer.parseInt(record.get("guestCount").trim());
    } catch (NumberFormatException | NullPointerException ex) {
      return new CsvRowError(row, "guestCount",
          "Guest count must be 1-50");
    }
    if (guestCount < 1 || guestCount > 50) {
      return new CsvRowError(row, "guestCount",
          "Guest count must be 1-50");
    }
    String escortCell = record.get("needsEscort");
    if (escortCell == null) {
      return new CsvRowError(row, "needsEscort",
          "needsEscort must be true or false");
    }
    String escortRaw = escortCell.trim().toLowerCase(Locale.ROOT);
    if (!escortRaw.equals("true") && !escortRaw.equals("false")) {
      return new CsvRowError(row, "needsEscort",
          "needsEscort must be true or false");
    }
    if (upsertCode == null) {
      String code = record.isMapped("requestCode")
          ? record.get("requestCode").trim() : "";
      if (!code.isEmpty() && findByRequestCode(code) != null) {
        return new CsvRowError(row, "requestCode",
            "Request already exists: " + code);
      }
    }
    return null;
  }

  private VisitRequest findByRequestCode(String code) {
    List<VisitRequest> found = entityManager.createQuery(
            "SELECT r FROM VisitRequest r WHERE r.requestCode = :code",
            VisitRequest.class)
        .setParameter("code", code)
        .getResultList();
    return found.isEmpty() ? null : found.get(0);
  }

  private void persistCreate(CSVRecord record) {
    VisitStatus status = VisitStatus.valueOf(record.get("status").trim()
        .toUpperCase(Locale.ROOT));
    Employee host = employees
        .findByUuid(UUID.fromString(
            record.get("hostEmployeeId").trim()))
        .orElseThrow();
    VisitRequest entity = new VisitRequest();
    entity.setUuid(UUID.randomUUID());
    entity.setRequestCode(nextRequestCode());
    Visitor visitor = new Visitor();
    visitor.setFullName(record.get("visitorFullName").trim());
    visitor.setDocumentType(DocumentType.valueOf(
        record.get("visitorDocumentType").trim()
            .toUpperCase(Locale.ROOT)));
    visitor.setDocumentNumber("");
    entity.setVisitor(visitors.save(visitor));
    entity.setHost(host);
    entity.setPurpose(record.get("purpose").trim());
    entity.setVisitDate(
        LocalDate.parse(record.get("visitDate").trim()));
    entity.setTimeFrom(
        LocalTime.parse(record.get("timeFrom").trim()));
    entity.setTimeTo(LocalTime.parse(record.get("timeTo").trim()));
    entity.setStatus(status);
    entity.setPassNumber(
        requiresPass(status) ? nextPassNumber() : null);
    entity.setGuestCount(
        Integer.parseInt(record.get("guestCount").trim()));
    entity.setNeedsEscort(Boolean.parseBoolean(
        record.get("needsEscort").trim()));
    entity.setArchived(false);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setTags(resolveTags(record.get("tags")));
    requests.save(entity);
  }

  private void persistUpdate(VisitRequest entity, CSVRecord record) {
    VisitStatus status = VisitStatus.valueOf(record.get("status").trim()
        .toUpperCase(Locale.ROOT));
    Employee host = employees
        .findByUuid(UUID.fromString(
            record.get("hostEmployeeId").trim()))
        .orElseThrow();
    if (requiresPass(status)
        && (entity.getPassNumber() == null
            || entity.getPassNumber().isBlank())) {
      entity.setPassNumber(nextPassNumber());
    }
    if (!requiresPass(status)) {
      entity.setPassNumber(null);
    }
    entity.getVisitor()
        .setFullName(record.get("visitorFullName").trim());
    entity.getVisitor().setDocumentType(DocumentType.valueOf(
        record.get("visitorDocumentType").trim()
            .toUpperCase(Locale.ROOT)));
    entity.setHost(host);
    entity.setPurpose(record.get("purpose").trim());
    entity.setVisitDate(
        LocalDate.parse(record.get("visitDate").trim()));
    entity.setTimeFrom(
        LocalTime.parse(record.get("timeFrom").trim()));
    entity.setTimeTo(LocalTime.parse(record.get("timeTo").trim()));
    entity.setStatus(status);
    entity.setGuestCount(
        Integer.parseInt(record.get("guestCount").trim()));
    entity.setNeedsEscort(Boolean.parseBoolean(
        record.get("needsEscort").trim()));
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setTags(resolveTags(record.get("tags")));
    requests.save(entity);
  }

  private Set<VisitTag> resolveTags(String raw) {
    Set<VisitTag> result = new LinkedHashSet<>();
    if (raw == null || raw.isBlank()) {
      return result;
    }
    for (String part : raw.split(";")) {
      String normalized = part.trim().toLowerCase(Locale.ROOT);
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
    return status == VisitStatus.ISSUED
        || status == VisitStatus.COMPLETED;
  }
}
