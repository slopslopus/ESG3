package ru.mirea.visitpass.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RequestResponse {

  private UUID id;
  private String requestCode;
  private long version;
  private VisitorPart visitor;
  private HostPart host;
  private String purpose;
  private LocalDate visitDate;
  private LocalTime timeFrom;
  private LocalTime timeTo;
  private String status;
  private String passNumber;
  private LocalDateTime arrivedAt;
  private LocalDateTime leftAt;
  private int guestCount;
  private boolean needsEscort;
  private String risk;
  private boolean archived;
  private Set<String> tags;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public record VisitorPart(String fullName, String documentType,
      String documentNumber) {
  }

  public record HostPart(UUID id, String fullName, String department) {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getRequestCode() {
    return requestCode;
  }

  public void setRequestCode(String requestCode) {
    this.requestCode = requestCode;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public VisitorPart getVisitor() {
    return visitor;
  }

  public void setVisitor(VisitorPart visitor) {
    this.visitor = visitor;
  }

  public HostPart getHost() {
    return host;
  }

  public void setHost(HostPart host) {
    this.host = host;
  }

  public String getPurpose() {
    return purpose;
  }

  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }

  public LocalDate getVisitDate() {
    return visitDate;
  }

  public void setVisitDate(LocalDate visitDate) {
    this.visitDate = visitDate;
  }

  public LocalTime getTimeFrom() {
    return timeFrom;
  }

  public void setTimeFrom(LocalTime timeFrom) {
    this.timeFrom = timeFrom;
  }

  public LocalTime getTimeTo() {
    return timeTo;
  }

  public void setTimeTo(LocalTime timeTo) {
    this.timeTo = timeTo;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getPassNumber() {
    return passNumber;
  }

  public void setPassNumber(String passNumber) {
    this.passNumber = passNumber;
  }

  public LocalDateTime getArrivedAt() {
    return arrivedAt;
  }

  public void setArrivedAt(LocalDateTime arrivedAt) {
    this.arrivedAt = arrivedAt;
  }

  public LocalDateTime getLeftAt() {
    return leftAt;
  }

  public void setLeftAt(LocalDateTime leftAt) {
    this.leftAt = leftAt;
  }

  public int getGuestCount() {
    return guestCount;
  }

  public void setGuestCount(int guestCount) {
    this.guestCount = guestCount;
  }

  public boolean isNeedsEscort() {
    return needsEscort;
  }

  public void setNeedsEscort(boolean needsEscort) {
    this.needsEscort = needsEscort;
  }

  public String getRisk() {
    return risk;
  }

  public void setRisk(String risk) {
    this.risk = risk;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<String> tagList() {
    return tags == null ? List.of() : List.copyOf(tags);
  }
}
