package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.Employee;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

  Optional<Employee> findByUuid(UUID uuid);
}
