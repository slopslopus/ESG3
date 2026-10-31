package ru.mirea.visitpass.filter;

import java.util.UUID;

public class RequestFilter {

  private String q;
  private UUID employeeId;
  private String status;
  private Boolean escort;
  private String risk;
  private String tag;
  private String archived = "active";
  private Integer minGuests;
  private Integer maxGuests;

  public String getQ() {
    return q;
  }

  public void setQ(String q) {
    this.q = q;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public void setEmployeeId(UUID employeeId) {
    this.employeeId = employeeId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Boolean getEscort() {
    return escort;
  }

  public void setEscort(Boolean escort) {
    this.escort = escort;
  }

  public String getRisk() {
    return risk;
  }

  public void setRisk(String risk) {
    this.risk = risk;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getArchived() {
    return archived;
  }

  public void setArchived(String archived) {
    this.archived = archived;
  }

  public Integer getMinGuests() {
    return minGuests;
  }

  public void setMinGuests(Integer minGuests) {
    this.minGuests = minGuests;
  }

  public Integer getMaxGuests() {
    return maxGuests;
  }

  public void setMaxGuests(Integer maxGuests) {
    this.maxGuests = maxGuests;
  }
}
