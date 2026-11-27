package ru.mirea.visitpass.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.dto.bulk.BulkRequest;
import ru.mirea.visitpass.dto.bulk.BulkResponse;
import ru.mirea.visitpass.dto.request.CreateRequestDto;
import ru.mirea.visitpass.dto.request.RequestResponse;
import ru.mirea.visitpass.dto.request.UpdateRequestDto;
import ru.mirea.visitpass.error.ForbiddenException;
import ru.mirea.visitpass.filter.RequestFilter;
import ru.mirea.visitpass.service.BulkService;
import ru.mirea.visitpass.service.RequestService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> SORTABLE =
      Set.of("visitDate", "createdAt", "guestCount");

  private final RequestService service;

  private final BulkService bulk;

  public RequestController(RequestService service, BulkService bulk) {
    this.service = service;
    this.bulk = bulk;
  }

  @GetMapping
  public Page<RequestResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Boolean escort,
      @RequestParam(required = false) String risk,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false, defaultValue = "active") String archived,
      @RequestParam(required = false) Integer minGuests,
      @RequestParam(required = false) Integer maxGuests,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false, defaultValue = "visitDate") String sort,
      @RequestParam(required = false, defaultValue = "asc") String dir) {
    requireAny("VIEWER", "OPERATOR", "ADMIN");
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "size must be between 1 and " + MAX_PAGE_SIZE);
    }
    String property = mapSort(sort);
    Sort.Direction direction =
        "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(direction, property));
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
    return service.list(filter, pageable);
  }

  @GetMapping("/{id}")
  public RequestResponse get(@PathVariable UUID id) {
    requireAny("VIEWER", "OPERATOR", "ADMIN");
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<RequestResponse> create(
      @Valid @RequestBody CreateRequestDto dto) {
    requireAny("OPERATOR", "ADMIN");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(dto));
  }

  @PutMapping("/{id}")
  public RequestResponse update(@PathVariable UUID id,
      @Valid @RequestBody UpdateRequestDto dto) {
    requireAny("OPERATOR", "ADMIN");
    return service.update(id, dto);
  }

  @PostMapping("/bulk")
  public BulkResponse bulk(@Valid @RequestBody BulkRequest dto) {
    requireAny("OPERATOR", "ADMIN");
    return bulk.bulk(dto);
  }

  @DeleteMapping("/{id}")
  public RequestResponse archive(@PathVariable UUID id) {
    requireAny("OPERATOR", "ADMIN");
    return service.archive(id);
  }

  @PostMapping("/{id}/restore")
  public RequestResponse restore(@PathVariable UUID id) {
    requireAny("OPERATOR", "ADMIN");
    return service.restore(id);
  }

  @DeleteMapping("/{id}/permanent")
  public ResponseEntity<Map<String, String>> deletePermanent(
      @PathVariable UUID id) {
    requireAny("ADMIN");
    service.deletePermanent(id);
    return ResponseEntity.ok(Map.of("status", "deleted"));
  }

  private static String mapSort(String sort) {
    if (!SORTABLE.contains(sort)) {
      throw new IllegalArgumentException("Unknown sort field: " + sort);
    }
    return Map.of(
        "visitDate", "visitDate",
        "createdAt", "createdAt",
        "guestCount", "guestCount").get(sort);
  }

  // Manual role check: SecurityConfig is owned by another module and must not
  // be touched, so @PreAuthorize cannot be enabled from here. The hierarchy
  // ADMIN > OPERATOR > VIEWER (see SecurityConfig.roleHierarchy) is
  // expanded explicitly below.
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
    throw new ForbiddenException("Access denied");
  }
}
