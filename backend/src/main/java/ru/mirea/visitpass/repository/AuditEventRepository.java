package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.mirea.visitpass.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>,
    JpaSpecificationExecutor<AuditEvent> {
}
