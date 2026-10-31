package ru.mirea.visitpass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "visit_requests")
public class VisitRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uuid", nullable = false, unique = true, updatable = false)
  private UUID uuid;

  @Column(name = "request_code", nullable = false, unique = true,
      updatable = false)
  private String requestCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "visitor_id", nullable = false)
  private Visitor visitor;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "host_id", nullable = false)
  private Employee host;

  @Column(name = "purpose", nullable = false)
  private String purpose;

  @Column(name = "visit_date", nullable = false)
  private LocalDate visitDate;

  @Column(name = "time_from", nullable = false)
  private LocalTime timeFrom;

  @Column(name = "time_to", nullable = false)
  private LocalTime timeTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private VisitStatus status;

  @Column(name = "pass_number", unique = true)
  private String passNumber;

  @Column(name = "arrived_at")
  private LocalDateTime arrivedAt;

  @Column(name = "left_at")
  private LocalDateTime leftAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "guest_count", nullable = false)
  private int guestCount;

  @Column(name = "needs_escort", nullable = false)
  private boolean needsEscort;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "archived", nullable = false)
  private boolean archived;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @ManyToMany
  @JoinTable(name = "visit_request_tags",
      joinColumns = @JoinColumn(name = "request_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  private Set<VisitTag> tags = new HashSet<>();

  public Long getId() {
    return id;
  }

  public UUID getUuid() {
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  public String getRequestCode() {
    return requestCode;
  }

  public void setRequestCode(String requestCode) {
    this.requestCode = requestCode;
  }

  public Visitor getVisitor() {
    return visitor;
  }

  public void setVisitor(Visitor visitor) {
    this.visitor = visitor;
  }

  public Employee getHost() {
    return host;
  }

  public void setHost(Employee host) {
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

  public VisitStatus getStatus() {
    return status;
  }

  public void setStatus(VisitStatus status) {
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
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

  public Long getVersion() {
    return version;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Set<VisitTag> getTags() {
    return tags;
  }

  public void setTags(Set<VisitTag> tags) {
    this.tags = tags;
  }
}
