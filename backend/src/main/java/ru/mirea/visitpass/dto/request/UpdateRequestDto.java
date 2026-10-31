package ru.mirea.visitpass.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public class UpdateRequestDto {

  @NotNull
  private Long version;

  @NotBlank
  @Size(min = 2, max = 150)
  private String visitorFullName;

  @NotBlank
  private String visitorDocumentType;

  @NotBlank
  @Size(max = 30)
  private String visitorDocumentNumber;

  @NotNull
  private UUID hostEmployeeId;

  @NotBlank
  @Size(min = 5, max = 1000)
  private String purpose;

  @NotNull
  @VisitDateRange
  private LocalDate visitDate;

  @NotNull
  private LocalTime timeFrom;

  @NotNull
  private LocalTime timeTo;

  private String status;

  @Min(1)
  @Max(50)
  private Integer guestCount;

  private Boolean needsEscort;

  private Set<String> tags;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public String getVisitorFullName() {
    return visitorFullName;
  }

  public void setVisitorFullName(String visitorFullName) {
    this.visitorFullName = visitorFullName;
  }

  public String getVisitorDocumentType() {
    return visitorDocumentType;
  }

  public void setVisitorDocumentType(String visitorDocumentType) {
    this.visitorDocumentType = visitorDocumentType;
  }

  public String getVisitorDocumentNumber() {
    return visitorDocumentNumber;
  }

  public void setVisitorDocumentNumber(String visitorDocumentNumber) {
    this.visitorDocumentNumber = visitorDocumentNumber;
  }

  public UUID getHostEmployeeId() {
    return hostEmployeeId;
  }

  public void setHostEmployeeId(UUID hostEmployeeId) {
    this.hostEmployeeId = hostEmployeeId;
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

  public Integer getGuestCount() {
    return guestCount;
  }

  public void setGuestCount(Integer guestCount) {
    this.guestCount = guestCount;
  }

  public Boolean getNeedsEscort() {
    return needsEscort;
  }

  public void setNeedsEscort(Boolean needsEscort) {
    this.needsEscort = needsEscort;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }
}
