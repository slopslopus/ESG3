package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
