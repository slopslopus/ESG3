package ru.mirea.visitpass.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.visitpass.error.ForbiddenException;
import ru.mirea.visitpass.filter.RequestFilter;
import ru.mirea.visitpass.service.CsvService;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
public class ImportController {

  private final CsvService csv;

  public ImportController(CsvService csv) {
    this.csv = csv;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Boolean escort,
      @RequestParam(required = false) String risk,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false, defaultValue = "active")
      String archived,
      @RequestParam(required = false) Integer minGuests,
      @RequestParam(required = false) Integer maxGuests) {
    requireAny("VIEWER", "OPERATOR", "ADMIN");
    RequestFilter filter = new RequestFilter();
    filter.setQ(q);
    filter.setEmployeeId(employeeId);
    filter.setStatus(status);
    filter.setEscort(escort);
    filter.setRisk(risk);
    filter.setTag(tag);
    filter.setArchived(archived);
    filter.setMinGuests(minGuests);
    filter.setMaxGuests(maxGuests);
    byte[] body = csv.exportCsv(filter);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"requests.csv\"")
        .body(body);
  }

  @PostMapping("/import/preview")
  public Map<String, Object> preview(
      @RequestParam("file") MultipartFile file) throws IOException {
    requireAny("OPERATOR", "ADMIN");
    CsvService.PreviewResult result =
        csv.preview(file.getInputStream());
    return Map.of("validCount", result.validCount(),
        "errors", toMaps(result.errors()));
  }

  @PostMapping("/import/apply")
  public Map<String, Object> apply(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false, defaultValue = "CREATE_ONLY")
      String mode) throws IOException {
    requireAny("OPERATOR", "ADMIN");
    CsvService.ImportMode parsed;
    try {
      parsed = CsvService.ImportMode.valueOf(
          mode.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown mode: " + mode);
    }
    CsvService.ApplyResult result =
        csv.apply(file.getInputStream(), parsed);
    return Map.of("created", result.created(),
        "updated", result.updated(),
        "errors", toMaps(result.errors()));
  }

  private static List<Map<String, Object>> toMaps(
      List<CsvService.CsvRowError> errors) {
    return errors.stream()
        .map(e -> Map.<String, Object>of("row", e.row(),
            "field", e.field(), "message", e.message()))
        .collect(Collectors.toList());
  }

  private static void requireAny(String... allowed) {
    Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
    Set<String> have = auth == null ? Set.of()
        : auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    Set<String> expanded = new HashSet<>(have);
    if (expanded.contains("ROLE_ADMIN")) {
      expanded.add("ROLE_OPERATOR");
      expanded.add("ROLE_VIEWER");
    }
    if (expanded.contains("ROLE_OPERATOR")) {
      expanded.add("ROLE_VIEWER");
    }
    for (String role : allowed) {
      if (expanded.contains("ROLE_" + role)) {
        return;
      }
    }
    HttpStatus status = auth == null
        || !auth.isAuthenticated()
        || have.contains("ROLE_ANONYMOUS")
        ? HttpStatus.UNAUTHORIZED
        : HttpStatus.FORBIDDEN;
    if (status == HttpStatus.UNAUTHORIZED) {
      throw new org.springframework.security.authentication
          .InsufficientAuthenticationException("Authentication required");
    }
    throw new ForbiddenException("Access denied");
  }
}
