package com.abzed.template.config;

import com.abzed.template.user.AuthProvider;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds (or promotes) an administrator on startup. Active only when
 * {@code APP_ADMIN_EMAIL} is set, so non-admin deployments are unaffected.
 * Provide {@code APP_ADMIN_PASSWORD} to create the account if it does not exist.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.admin", name = "email")
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByEmail(adminEmail).ifPresentOrElse(
                this::promoteIfNeeded,
                this::createIfPossible
        );
    }

    private void promoteIfNeeded(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("Promoted existing user to ADMIN: {}", adminEmail);
        }
    }

    private void createIfPossible() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("app.admin.email is set but app.admin.password is empty; skipping admin creation");
            return;
        }
        User admin = new User();
        admin.setFullName("Administrator");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setProvider(AuthProvider.LOCAL);
        admin.setEmailVerified(true);
        userRepository.save(admin);
        log.info("Created ADMIN user: {}", adminEmail);
    }
}
