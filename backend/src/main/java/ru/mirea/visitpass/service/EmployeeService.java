package ru.mirea.visitpass.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.EmployeeRole;
import ru.mirea.visitpass.dto.employee.EmployeeDto;
import ru.mirea.visitpass.dto.employee.EmployeeResponse;
import ru.mirea.visitpass.error.ResourceNotFoundException;
import ru.mirea.visitpass.repository.EmployeeRepository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmployeeService {

  private final EmployeeRepository repository;

  public EmployeeService(EmployeeRepository repository) {
    this.repository = repository;
  }

  public List<EmployeeResponse> list(Boolean active) {
    List<Employee> employees = repository.findAll();
    return employees.stream()
        .filter(e -> active == null || e.isActive() == active)
        .map(EmployeeService::toResponse)
        .toList();
  }

  public EmployeeResponse get(UUID uuid) {
    return toResponse(findByUuid(uuid));
  }

  @Transactional
  public EmployeeResponse create(EmployeeDto dto) {
    EmployeeRole role = parseRole(dto.getRole());
    validate(dto.getFullName(), dto.getDepartment());
    Employee employee = new Employee();
    employee.setUuid(UUID.randomUUID());
    employee.setFullName(dto.getFullName());
    employee.setDepartment(dto.getDepartment());
    employee.setRole(role);
    employee.setActive(true);
    // TODO(audit): record(REFERENCE_UPDATED, employee uuid)
    return toResponse(repository.save(employee));
  }

  @Transactional
  public EmployeeResponse update(UUID uuid, EmployeeDto dto) {
    Employee employee = findByUuid(uuid);
    EmployeeRole role = parseRole(dto.getRole());
    validate(dto.getFullName(), dto.getDepartment());
    employee.setFullName(dto.getFullName());
    employee.setDepartment(dto.getDepartment());
    employee.setRole(role);
    // TODO(audit): record(REFERENCE_UPDATED, employee uuid)
    return toResponse(repository.save(employee));
  }

  @Transactional
  public EmployeeResponse setActive(UUID uuid, boolean active) {
    Employee employee = findByUuid(uuid);
    employee.setActive(active);
    // TODO(audit): record(REFERENCE_UPDATED, employee uuid)
    return toResponse(repository.save(employee));
  }

  private Employee findByUuid(UUID uuid) {
    return repository.findByUuid(uuid)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Employee not found: " + uuid));
  }

  private static EmployeeResponse toResponse(Employee e) {
    return new EmployeeResponse(e.getUuid(), e.getFullName(),
        e.getDepartment(), e.getRole().name(), e.isActive());
  }

  private static EmployeeRole parseRole(String role) {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("role must not be blank");
    }
    try {
      return EmployeeRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown employee role: " + role);
    }
  }

  private static void validate(String fullName, String department) {
    if (fullName == null || fullName.isBlank()
        || fullName.trim().length() < 2 || fullName.length() > 150) {
      throw new IllegalArgumentException(
          "fullName must be 2..150 chars");
    }
    if (department == null || department.isBlank()
        || department.trim().length() < 2 || department.length() > 100) {
      throw new IllegalArgumentException(
          "department must be 2..100 chars");
    }
  }
}
