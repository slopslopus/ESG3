package ru.mirea.visitpass.filter;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.visitpass.domain.Employee;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.VisitTag;
import ru.mirea.visitpass.domain.Visitor;

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
}
