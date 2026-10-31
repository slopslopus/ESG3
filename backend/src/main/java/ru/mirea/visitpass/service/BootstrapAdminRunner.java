package ru.mirea.visitpass.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import ru.mirea.visitpass.config.AppProperties;
import ru.mirea.visitpass.domain.UserAccount;
import ru.mirea.visitpass.domain.UserRole;
import ru.mirea.visitpass.repository.UserAccountRepository;

import java.util.EnumSet;
import java.util.UUID;

@Component
@Order(Integer.MAX_VALUE)
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AppProperties properties;

  public BootstrapAdminRunner(UserAccountRepository users,
      PasswordEncoder passwordEncoder, AppProperties properties) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (users.count() > 0) {
      return;
    }
    AppProperties.BootstrapAdmin admin = properties.getBootstrapAdmin();
    Assert.hasText(admin.getEmail(), "Bootstrap admin email must be configured");
    Assert.hasText(admin.getPassword(), "Bootstrap admin password must be configured");
    UserAccount user = new UserAccount();
    user.setUuid(UUID.randomUUID());
    user.setEmail(admin.getEmail());
    user.setName(admin.getName());
    user.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
    user.setActive(true);
    user.setMustChangePassword(true);
    user.setFailedAttempts(0);
    user.setRoles(EnumSet.of(UserRole.ADMIN));
    users.save(user);
    log.info("Bootstrap admin created: email={}", admin.getEmail());
  }
}
