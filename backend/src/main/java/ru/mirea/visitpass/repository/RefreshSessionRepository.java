package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.RefreshSession;

import java.util.Optional;

public interface RefreshSessionRepository
    extends JpaRepository<RefreshSession, Long> {

  Optional<RefreshSession> findByTokenHash(String tokenHash);
}
