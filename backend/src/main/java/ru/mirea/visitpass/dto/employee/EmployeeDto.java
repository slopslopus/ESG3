package ru.mirea.visitpass.dto.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmployeeDto {

  @NotBlank(message = "fullName must not be blank")
  @Size(min = 2, max = 150, message = "fullName must be 2..150 chars")
  private String fullName;

  @NotBlank(message = "department must not be blank")
  @Size(min = 2, max = 100, message = "department must be 2..100 chars")
  private String department;

  @NotBlank(message = "role must not be blank")
  private String role;

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }
}
