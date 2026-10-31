package ru.mirea.visitpass.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;
import ru.mirea.visitpass.dto.admin.AuditEventDto;
import ru.mirea.visitpass.dto.admin.CreateUserDto;
import ru.mirea.visitpass.dto.admin.CreateUserResponse;
import ru.mirea.visitpass.dto.admin.ResetPasswordResponse;
import ru.mirea.visitpass.dto.admin.UpdateUserDto;
import ru.mirea.visitpass.dto.admin.UserResponse;
import ru.mirea.visitpass.error.ResourceNotFoundException;
import ru.mirea.visitpass.filter.AuditSpecifications;
import ru.mirea.visitpass.repository.AuditEventRepository;
import ru.mirea.visitpass.repository.UserAccountRepository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

  private static final int TEMP_PASSWORD_LENGTH = 12;
  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private final UserAccountRepository users;
  private final AuditEventRepository auditEvents;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom random = new SecureRandom();

  public AdminService(UserAccountRepository users,
      AuditEventRepository auditEvents, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.auditEvents = auditEvents;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public Page<UserResponse> listUsers(Pageable pageable) {
    return users.findAll(sanitize(pageable, Set.of("email", "name"),
        Sort.unsorted())).map(this::toResponse);
  }

  @Transactional
  public CreateUserResponse createUser(CreateUserDto dto) {
    if (users.findByEmail(dto.email()).isPresent()) {
      throw new IllegalArgumentException("Email already exists");
    }
    Set<UserRole> roles = parseRoles(dto.roles());
    String temporaryPassword = generateTemporaryPassword();
    UserAccount user = new UserAccount();
    user.setUuid(UUID.randomUUID());
    user.setEmail(dto.email());
    user.setName(dto.name());
    user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
    user.setActive(true);
    user.setMustChangePassword(true);
    user.setFailedAttempts(0);
    user.setRoles(roles);
    users.save(user);
    // TODO(audit): persist USER_CREATED audit event (AuditService is Task 4 scope)
    UserResponse base = toResponse(user);
    return new CreateUserResponse(base.id(), base.email(), base.name(),
        base.roles(), base.active(), base.mustChangePassword(),
        temporaryPassword);
  }

  @Transactional
  public UserResponse updateUser(UUID id, UpdateUserDto dto) {
    UserAccount user = users.findByUuid(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if (dto.name() != null) {
      user.setName(dto.name());
    }
    if (dto.email() != null && !dto.email().equals(user.getEmail())) {
      if (users.findByEmail(dto.email()).isPresent()) {
        throw new IllegalArgumentException("Email already exists");
      }
      user.setEmail(dto.email());
    }
    if (dto.roles() != null) {
      user.setRoles(parseRoles(dto.roles()));
    }
    if (dto.active() != null) {
      user.setActive(dto.active());
    }
    users.save(user);
    // TODO(audit): persist USER_UPDATED audit event (AuditService is Task 4 scope)
    return toResponse(user);
  }

  @Transactional
  public ResetPasswordResponse resetPassword(UUID id) {
    UserAccount user = users.findByUuid(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    String temporaryPassword = generateTemporaryPassword();
    user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
    user.setMustChangePassword(true);
    users.save(user);
    // TODO(audit): persist USER_PASSWORD_RESET audit event (AuditService is Task 4 scope)
    return new ResetPasswordResponse(temporaryPassword);
  }

  @Transactional(readOnly = true)
  public Page<AuditEventDto> listAudit(String q, String eventType,
      Pageable pageable) {
    Pageable safe = sanitize(pageable, Set.of("createdAt", "eventType"),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    return auditEvents
        .findAll(AuditSpecifications.filter(q, eventType), safe)
        .map(e -> new AuditEventDto(
            e.getCreatedAt(),
            e.getEventType(),
            e.getEntityType(),
            e.getEntityUuid(),
            e.getDescription(),
            e.getCorrelationId(),
            e.getUser() == null ? null : e.getUser().getEmail()));
  }

  private static Pageable sanitize(Pageable pageable, Set<String> allowed,
      Sort fallback) {
    List<Sort.Order> orders = new ArrayList<>();
    for (Sort.Order order : pageable.getSort()) {
      if (allowed.contains(order.getProperty())) {
        orders.add(order);
      }
    }
    Sort sort = orders.isEmpty() ? fallback : Sort.by(orders);
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
        sort);
  }

  private Set<UserRole> parseRoles(Set<String> names) {
    if (names == null || names.isEmpty()) {
      throw new IllegalArgumentException("At least one role is required");
    }
    try {
      return names.stream()
          .map(String::toUpperCase)
          .map(UserRole::valueOf)
          .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown role: " + names);
    }
  }

  private String generateTemporaryPassword() {
    String password;
    do {
      StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
      for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
        sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      password = sb.toString();
    } while (!passesPolicy(password));
    return password;
  }

  private static boolean passesPolicy(String value) {
    try {
      PasswordPolicy.validate(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private UserResponse toResponse(UserAccount user) {
    Set<String> roles = user.getRoles().stream()
        .map(UserRole::name)
        .collect(Collectors.toSet());
    return new UserResponse(user.getUuid(), user.getEmail(), user.getName(),
        roles, user.isActive(), user.isMustChangePassword());
  }
}
