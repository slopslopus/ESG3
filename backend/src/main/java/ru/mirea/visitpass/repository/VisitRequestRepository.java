package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.mirea.visitpass.domain.VisitRequest;

import java.util.Optional;
import java.util.UUID;

public interface VisitRequestRepository
    extends JpaRepository<VisitRequest, Long>,
    JpaSpecificationExecutor<VisitRequest> {

  Optional<VisitRequest> findByUuid(UUID uuid);
}
