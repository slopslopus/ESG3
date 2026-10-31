package ru.mirea.visitpass.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.dto.employee.EmployeeDto;
import ru.mirea.visitpass.dto.employee.EmployeeResponse;
import ru.mirea.visitpass.error.ForbiddenException;
import ru.mirea.visitpass.service.EmployeeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

  private final EmployeeService service;

  public EmployeeController(EmployeeService service) {
    this.service = service;
  }

  private static void requireAdmin() {
    Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
    boolean admin = auth != null && auth.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    if (!admin) {
      throw new ForbiddenException("Admin role required");
    }
  }

  @GetMapping
  public List<EmployeeResponse> list(
      @RequestParam(required = false) Boolean active) {
    return service.list(active);
  }

  @GetMapping("/{id}")
  public EmployeeResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<EmployeeResponse> create(
      @Valid @RequestBody EmployeeDto dto) {
    requireAdmin();
    EmployeeResponse created = service.create(dto);
    // TODO(audit): record(REFERENCE_UPDATED, created employee uuid)
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{id}")
  public EmployeeResponse update(@PathVariable UUID id,
      @Valid @RequestBody EmployeeDto dto) {
    requireAdmin();
    EmployeeResponse updated = service.update(id, dto);
    // TODO(audit): record(REFERENCE_UPDATED, employee uuid)
    return updated;
  }

  @PostMapping("/{id}/enable")
  public EmployeeResponse enable(@PathVariable UUID id) {
    requireAdmin();
    EmployeeResponse updated = service.setActive(id, true);
    // TODO(audit): record(REFERENCE_UPDATED, employee uuid)
    return updated;
  }

  @PostMapping("/{id}/disable")
  public EmployeeResponse disable(@PathVariable UUID id) {
    requireAdmin();
    EmployeeResponse updated = service.setActive(id, false);
    // TODO(audit): record(REFERENCE_UPDATED, employee uuid)
    return updated;
  }
}
