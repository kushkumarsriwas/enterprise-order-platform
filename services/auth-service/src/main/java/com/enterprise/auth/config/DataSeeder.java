package com.enterprise.auth.config;

import com.enterprise.auth.user.Role;
import com.enterprise.auth.user.SellerProfile;
import com.enterprise.auth.user.SellerProfileRepository;
import com.enterprise.auth.user.User;
import com.enterprise.auth.user.UserRepository;
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
 * Optional demo accounts (app.seed.demo-accounts=true, used by the tests). Production starts empty:
 * customers and sellers register themselves; scripts/demo-data.sh fills a store through the public API.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-accounts", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String customerPassword;
    private final String sellerPassword;

    public DataSeeder(UserRepository userRepository, SellerProfileRepository sellerRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.customer-password:Customer@12345}") String customerPassword,
                      @Value("${app.seed.seller-password:Seller@12345}") String sellerPassword) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerPassword = customerPassword;
        this.sellerPassword = sellerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsernameIgnoreCase("customer")) {
            userRepository.save(new User("customer", "customer@nexora.dev", passwordEncoder.encode(customerPassword),
                    "Demo Customer", "+971500000001", Role.CUSTOMER));
            log.info("Seeded demo customer account 'customer'");
        }
        if (!userRepository.existsByUsernameIgnoreCase("seller")) {
            User seller = userRepository.save(new User("seller", "seller@nexora.dev",
                    passwordEncoder.encode(sellerPassword), "Demo Seller", "+971500000002", Role.SELLER));
            sellerRepository.save(new SellerProfile(seller, "Demo Store", "Demo store for testing", "+971500000002",
                    "Dubai", "United Arab Emirates", null, null));
            log.info("Seeded demo seller account 'seller'");
        }
    }
}
