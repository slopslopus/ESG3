package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.VisitNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {

  Optional<VisitNote> findByUuid(UUID uuid);

  List<VisitNote> findByRequestUuidOrderByCreatedAtAsc(UUID requestUuid);
}
