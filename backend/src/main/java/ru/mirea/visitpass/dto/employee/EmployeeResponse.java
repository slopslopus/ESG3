package ru.mirea.visitpass.dto.employee;

import java.util.UUID;

public class EmployeeResponse {

  private UUID id;
  private String fullName;
  private String department;
  private String role;
  private boolean active;

  public EmployeeResponse(UUID id, String fullName, String department,
      String role, boolean active) {
    this.id = id;
    this.fullName = fullName;
    this.department = department;
    this.role = role;
    this.active = active;
  }

  public UUID getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getDepartment() {
    return department;
  }

  public String getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }
}
