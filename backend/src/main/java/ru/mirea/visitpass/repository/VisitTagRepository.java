package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.VisitTag;

import java.util.Optional;

public interface VisitTagRepository extends JpaRepository<VisitTag, Long> {

  Optional<VisitTag> findByName(String name);
}
