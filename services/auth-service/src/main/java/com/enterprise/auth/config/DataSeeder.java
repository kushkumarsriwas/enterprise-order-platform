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

/** Creates the platform administrator on first start (no demo data: products and orders come from real users). */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final String customerPassword;
    private final boolean seedDemoCustomer;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      @Value("${app.seed.admin-password:Admin@12345}") String adminPassword,
                      @Value("${app.seed.customer-password:Customer@12345}") String customerPassword,
                      @Value("${app.seed.demo-customer:false}") boolean seedDemoCustomer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.customerPassword = customerPassword;
        this.seedDemoCustomer = seedDemoCustomer;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByRole(Role.ADMIN) && !userRepository.existsByUsernameIgnoreCase("admin")) {
            userRepository.save(new User("admin", "admin@enterprise-platform.dev",
                    passwordEncoder.encode(adminPassword), "Platform Administrator", null, Role.ADMIN));
            log.info("Seeded default admin account 'admin'");
        }
        if (seedDemoCustomer && !userRepository.existsByUsernameIgnoreCase("customer")) {
            userRepository.save(new User("customer", "customer@enterprise-platform.dev",
                    passwordEncoder.encode(customerPassword), "Demo Customer", null, Role.CUSTOMER));
            log.info("Seeded demo customer account 'customer'");
        }
    }
}
