package ru.mirea.visitpass.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.visitpass.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByUuid(UUID uuid);

  Optional<UserAccount> findByEmail(String email);
}
