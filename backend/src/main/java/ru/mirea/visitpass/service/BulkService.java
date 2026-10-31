package ru.mirea.visitpass.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mirea.visitpass.domain.VisitRequest;
import ru.mirea.visitpass.domain.VisitStatus;
import ru.mirea.visitpass.domain.VisitTag;
import ru.mirea.visitpass.dto.bulk.BulkRequest;
import ru.mirea.visitpass.dto.bulk.BulkResponse;
import ru.mirea.visitpass.error.ResourceNotFoundException;
import ru.mirea.visitpass.repository.VisitRequestRepository;
import ru.mirea.visitpass.repository.VisitTagRepository;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class BulkService {

  private static final String BULK_OPERATION = "BULK_OPERATION";

  private final VisitRequestRepository requests;
  private final VisitTagRepository tags;
  private final AuditService audits;
  private final TransactionTemplate requiresNew;

  @PersistenceContext
  private EntityManager entityManager;

  public BulkService(VisitRequestRepository requests,
      VisitTagRepository tags, AuditService audits,
      PlatformTransactionManager transactionManager) {
    this.requests = requests;
    this.tags = tags;
    this.audits = audits;
    this.requiresNew = new TransactionTemplate(transactionManager);
    this.requiresNew.setPropagationBehavior(
        TransactionTemplate.PROPAGATION_REQUIRES_NEW);
  }

  public BulkResponse bulk(BulkRequest dto) {
    String operation = dto.getOperation().trim().toUpperCase(Locale.ROOT);
    switch (operation) {
      case "ARCHIVE", "RESTORE", "SET_STATUS", "ADD_TAG", "REMOVE_TAG" -> {
      }
      default -> throw new IllegalArgumentException(
          "Unknown bulk operation: " + dto.getOperation());
    }
    String status = dto.getStatus();
    if (operation.equals("SET_STATUS")
        && (status == null || status.isBlank())) {
      throw new IllegalArgumentException(
          "status is required for SET_STATUS");
    }
    String tag = dto.getTag() == null ? null
        : dto.getTag().trim().toLowerCase(Locale.ROOT);
    if ((operation.equals("ADD_TAG") || operation.equals("REMOVE_TAG"))
        && (tag == null || tag.isEmpty())) {
      throw new IllegalArgumentException("tag is required for " + operation);
    }
    VisitStatus parsedStatus = null;
    String statusError = null;
    if (operation.equals("SET_STATUS")) {
      try {
        parsedStatus =
            VisitStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        statusError = "Unknown status: " + status;
      }
    }
    BulkResponse response = new BulkResponse();
    for (UUID id : dto.getIds()) {
      try {
        if (statusError != null) {
          throw new IllegalArgumentException(statusError);
        }
        applyOne(id, operation, parsedStatus, tag);
        response.getSucceeded().add(id);
      } catch (RuntimeException ex) {
        String message =
            ex.getMessage() == null ? "Operation failed" : ex.getMessage();
        response.getFailed().add(
            new BulkResponse.BulkFailure(id, message));
      }
    }
    audits.record(BULK_OPERATION, "VISIT_REQUEST", null,
        "op=" + operation + " ok=" + response.getSucceeded().size()
            + " fail=" + response.getFailed().size());
    return response;
  }

  private void applyOne(UUID id, String operation, VisitStatus status,
      String tag) {
    requiresNew.executeWithoutResult(tx -> {
      VisitRequest entity = requests.findByUuid(id)
          .orElseThrow(() -> new ResourceNotFoundException(
              "Request not found: " + id));
      switch (operation) {
        case "ARCHIVE" -> entity.setArchived(true);
        case "RESTORE" -> entity.setArchived(false);
        case "SET_STATUS" -> applyStatus(entity, status);
        case "ADD_TAG" -> entity.getTags().add(findOrCreateTag(tag));
        case "REMOVE_TAG" ->
            entity.getTags().removeIf(t -> t.getName().equals(tag));
        default -> throw new IllegalArgumentException(
            "Unknown bulk operation: " + operation);
      }
      entity.setUpdatedAt(LocalDateTime.now());
      requests.save(entity);
    });
  }

  private void applyStatus(VisitRequest entity, VisitStatus status) {
    if ((status == VisitStatus.ISSUED || status == VisitStatus.COMPLETED)
        && (entity.getPassNumber() == null
            || entity.getPassNumber().isBlank())) {
      entity.setPassNumber(nextPassNumber());
    }
    if ((status == VisitStatus.SUBMITTED
        || status == VisitStatus.CANCELLED)) {
      entity.setPassNumber(null);
    }
    entity.setStatus(status);
  }

  private VisitTag findOrCreateTag(String name) {
    return tags.findByName(name).orElseGet(() -> {
      VisitTag created = new VisitTag();
      created.setName(name);
      return tags.save(created);
    });
  }

  private String nextPassNumber() {
    Number next = (Number) entityManager
        .createNativeQuery("SELECT nextval('pass_number_seq')")
        .getSingleResult();
    return "PASS-" + String.format("%03d", next.longValue());
  }
}
