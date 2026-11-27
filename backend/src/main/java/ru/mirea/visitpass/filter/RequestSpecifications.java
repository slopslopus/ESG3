package ru.mirea.visitpass.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitRisk;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.VisitTag;
import ru.mirea.visitpass.domain.Visitor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RequestSpecifications {

  private RequestSpecifications() {
  }

  public static Specification<VisitRequest> byFilter(RequestFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.getEmployeeId() != null) {
        Join<VisitRequest, Employee> host =
            root.join("host", JoinType.INNER);
        predicates.add(cb.equal(host.get("uuid"), filter.getEmployeeId()));
      }
      if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
        try {
          VisitStatus status = VisitStatus.valueOf(
              filter.getStatus().trim().toUpperCase(Locale.ROOT));
          predicates.add(cb.equal(root.get("status"), status));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(
              "Unknown status: " + filter.getStatus());
        }
      }
      if (filter.getEscort() != null) {
        predicates.add(cb.equal(root.get("needsEscort"), filter.getEscort()));
      }
      if (filter.getRisk() != null && !filter.getRisk().isBlank()) {
        VisitRisk wanted;
        try {
          wanted = VisitRisk.valueOf(
              filter.getRisk().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(
              "Unknown risk: " + filter.getRisk());
        }
        predicates.add(riskPredicate(root, cb, wanted, LocalDate.now()));
      }
      if (filter.getTag() != null && !filter.getTag().isBlank()) {
        Join<VisitRequest, VisitTag> tag =
            root.join("tags", JoinType.INNER);
        predicates.add(cb.equal(cb.lower(tag.get("name")),
            filter.getTag().trim().toLowerCase(Locale.ROOT)));
      }
      String archived =
          filter.getArchived() == null ? "active" : filter.getArchived();
      switch (archived.trim().toLowerCase(Locale.ROOT)) {
        case "active" -> predicates.add(cb.isFalse(root.get("archived")));
        case "archived" -> predicates.add(cb.isTrue(root.get("archived")));
        case "all" -> {
        }
        default -> throw new IllegalArgumentException(
            "Unknown archived mode: " + filter.getArchived());
      }
      if (filter.getMinGuests() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("guestCount"),
            filter.getMinGuests()));
      }
      if (filter.getMaxGuests() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("guestCount"),
            filter.getMaxGuests()));
      }
      if (filter.getQ() != null && !filter.getQ().isBlank()) {
        String pattern =
            "%" + filter.getQ().trim().toLowerCase(Locale.ROOT) + "%";
        Join<VisitRequest, Visitor> visitor =
            root.join("visitor", JoinType.INNER);
        Join<VisitRequest, Employee> host =
            root.join("host", JoinType.INNER);
        predicates.add(cb.or(
            cb.like(cb.lower(visitor.get("fullName")), pattern),
            cb.like(cb.lower(host.get("fullName")), pattern),
            cb.like(cb.lower(root.get("purpose")), pattern),
            cb.like(cb.lower(root.get("requestCode")), pattern),
            cb.like(cb.lower(root.get("passNumber")), pattern)));
      }
      query.distinct(true);
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Mirrors {@code VisitRiskCalculator}: LOW short-circuits first,
   * then HIGH, otherwise MEDIUM.
   */
  private static Predicate riskPredicate(Root<VisitRequest> root,
      CriteriaBuilder cb, VisitRisk wanted, LocalDate today) {
    Predicate low = cb.or(
        cb.equal(root.get("status"), VisitStatus.CANCELLED),
        cb.and(
            cb.equal(root.get("status"), VisitStatus.COMPLETED),
            cb.lessThan(root.get("visitDate"), today)));
    Predicate high = cb.or(
        cb.and(
            cb.isTrue(root.get("needsEscort")),
            cb.greaterThanOrEqualTo(root.get("guestCount"), 3)),
        cb.and(
            cb.equal(root.get("status"), VisitStatus.SUBMITTED),
            cb.lessThanOrEqualTo(root.get("visitDate"),
                today.plusDays(1))));
    return switch (wanted) {
      case LOW -> low;
      case HIGH -> cb.and(cb.not(low), high);
      case MEDIUM -> cb.and(cb.not(low), cb.not(high));
    };
  }
}
