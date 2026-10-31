package ru.mirea.visitpass.dto.analytics;

import java.util.List;
import java.util.Map;

public class AnalyticsResponse {

  private long total;
  private long active;
  private long archived;
  private long escortCount;
  private double avgGuestCount;
  private List<EmployeeStat> byEmployee;
  private Map<String, Long> byStatus;
  private Map<String, Long> byRisk;

  public AnalyticsResponse(long total, long active, long archived,
      long escortCount, double avgGuestCount,
      List<EmployeeStat> byEmployee,
      Map<String, Long> byStatus, Map<String, Long> byRisk) {
    this.total = total;
    this.active = active;
    this.archived = archived;
    this.escortCount = escortCount;
    this.avgGuestCount = avgGuestCount;
    this.byEmployee = byEmployee;
    this.byStatus = byStatus;
    this.byRisk = byRisk;
  }

  public long getTotal() {
    return total;
  }

  public long getActive() {
    return active;
  }

  public long getArchived() {
    return archived;
  }

  public long getEscortCount() {
    return escortCount;
  }

  public double getAvgGuestCount() {
    return avgGuestCount;
  }

  public List<EmployeeStat> getByEmployee() {
    return byEmployee;
  }

  public Map<String, Long> getByStatus() {
    return byStatus;
  }

  public Map<String, Long> getByRisk() {
    return byRisk;
  }

  public static class EmployeeStat {

    private String employeeId;
    private String employeeName;
    private long count;

    public EmployeeStat(String employeeId, String employeeName, long count) {
      this.employeeId = employeeId;
      this.employeeName = employeeName;
      this.count = count;
    }

    public String getEmployeeId() {
      return employeeId;
    }

    public String getEmployeeName() {
      return employeeName;
    }

    public long getCount() {
      return count;
    }
  }
}
