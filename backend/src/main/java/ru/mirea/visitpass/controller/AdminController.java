package ru.mirea.visitpass.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.visitpass.dto.admin.AuditEventDto;
import ru.mirea.visitpass.dto.admin.CreateUserDto;
import ru.mirea.visitpass.dto.admin.CreateUserResponse;
import ru.mirea.visitpass.dto.admin.ResetPasswordResponse;
import ru.mirea.visitpass.dto.admin.UpdateUserDto;
import ru.mirea.visitpass.dto.admin.UserResponse;
import ru.mirea.visitpass.service.AdminService;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

  private static final int MAX_PAGE_SIZE = 100;

  private static final Set<String> AUDIT_SORTABLE =
      Set.of("createdAt", "eventType");

  private final AdminService adminService;

  public AdminController(AdminService adminService) {
    this.adminService = adminService;
  }

  @GetMapping("/users")
  public Page<UserResponse> listUsers(
      @PageableDefault(size = 20) Pageable pageable) {
    return adminService.listUsers(pageable);
  }

  @PostMapping("/users")
  public ResponseEntity<CreateUserResponse> createUser(
      @Valid @RequestBody CreateUserDto dto) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminService.createUser(dto));
  }

  @PutMapping("/users/{id}")
  public UserResponse updateUser(@PathVariable UUID id,
      @Valid @RequestBody UpdateUserDto dto) {
    return adminService.updateUser(id, dto);
  }

  @PostMapping("/users/{id}/reset-password")
  public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
    return adminService.resetPassword(id);
  }

  @GetMapping("/audit")
  public Page<AuditEventDto> listAudit(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false, defaultValue = "createdAt") String sort,
      @RequestParam(required = false, defaultValue = "desc") String dir) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "size must be between 1 and " + MAX_PAGE_SIZE);
    }
    Sort.Direction direction =
        "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(direction, mapAuditSort(sort)));
    return adminService.listAudit(q, eventType, pageable);
  }

  private static String mapAuditSort(String sort) {
    if (!AUDIT_SORTABLE.contains(sort)) {
      throw new IllegalArgumentException("Unknown sort field: " + sort);
    }
    return sort;
  }
}
