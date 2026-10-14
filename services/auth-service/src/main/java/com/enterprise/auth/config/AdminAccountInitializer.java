package com.enterprise.auth.config;

import com.enterprise.auth.user.Role;
import com.enterprise.auth.user.User;
import com.enterprise.auth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the platform owner's admin account from configuration (ADMIN_USERNAME / ADMIN_PASSWORD).
 * There is no API to create administrators, so there is exactly one. Changing ADMIN_PASSWORD and
 * restarting rotates the password. Without ADMIN_PASSWORD no admin account exists (safe default).
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.username:admin}") String username,
                                   @Value("${app.admin.email:admin@nexora.local}") String email,
                                   @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("ADMIN_PASSWORD is not set - the admin console is disabled until it is configured");
            return;
        }
        if (password.length() < 10) {
            log.warn("ADMIN_PASSWORD is shorter than 10 characters - please use a stronger password");
        }
        userRepository.findFirstByRole(Role.ADMIN).ifPresentOrElse(admin -> {
            if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
                admin.changePassword(passwordEncoder.encode(password));
                log.info("Admin password rotated from configuration");
            }
        }, () -> {
            if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
                log.error("Cannot create the admin account: username '{}' or e-mail '{}' is already used", username, email);
                return;
            }
            userRepository.save(new User(username, email, passwordEncoder.encode(password), "Platform administrator",
                    null, Role.ADMIN));
            log.info("Created the platform admin account '{}'", username);
        });
    }
}
