package ru.mirea.visitpass.service;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.visitpass.domain.AuditEvent;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.error.GlobalExceptionHandler;
import ru.mirea.visitpass.repository.AuditEventRepository;
import ru.mirea.visitpass.repository.UserAccountRepository;
import ru.mirea.visitpass.security.CurrentUser;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditService {

  public static final String AUTH_LOGIN = "AUTH_LOGIN";

  public static final String AUTH_LOGOUT = "AUTH_LOGOUT";

  public static final String AUTH_FAILED = "AUTH_FAILED";

  public static final String AUTH_PASSWORD_CHANGED = "AUTH_PASSWORD_CHANGED";

  private final AuditEventRepository audits;

  private final UserAccountRepository users;

  public AuditService(AuditEventRepository audits, UserAccountRepository users) {
    this.audits = audits;
    this.users = users;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String eventType, String entityType, UUID entityUuid,
      String description) {
    UserAccount user = null;
    try {
      CurrentUser current = CurrentUser.current();
      if (current != null) {
        user = users.findByUuid(current.id()).orElse(null);
      }
    } catch (IllegalStateException ex) {
      user = null;
    }
    AuditEvent event = new AuditEvent();
    event.setUser(user);
    event.setEventType(eventType);
    event.setEntityType(entityType);
    event.setEntityUuid(entityUuid);
    event.setCreatedAt(LocalDateTime.now());
    event.setDescription(description);
    event.setCorrelationId(MDC.get(GlobalExceptionHandler.CORRELATION_ID_KEY));
    audits.save(event);
  }
}
