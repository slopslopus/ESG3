package ru.mirea.visitpass.filter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.visitpass.domain.AuditEvent;

import java.util.ArrayList;
import java.util.List;

public final class AuditSpecifications {

  private AuditSpecifications() {
  }

  public static Specification<AuditEvent> filter(String q, String eventType) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (q != null && !q.isBlank()) {
        String like = "%" + q.toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("description")), like),
            cb.like(cb.lower(root.get("eventType")), like),
            cb.like(cb.lower(root.get("entityType")), like),
            cb.like(cb.lower(root.get("correlationId")), like)));
      }
      if (eventType != null && !eventType.isBlank()) {
        predicates.add(cb.equal(root.get("eventType"), eventType));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
