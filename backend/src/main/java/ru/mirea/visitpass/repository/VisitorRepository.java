package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.Visitor;

import java.util.Optional;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

  Optional<Visitor> findByFullName(String fullName);
}
