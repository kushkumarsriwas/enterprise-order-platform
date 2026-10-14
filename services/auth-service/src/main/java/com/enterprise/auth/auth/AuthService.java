package com.enterprise.auth.auth;

import com.enterprise.auth.audit.ClientInfo;
import com.enterprise.auth.audit.LoginAuditService;
import com.enterprise.auth.auth.AuthDtos.AccountType;
import com.enterprise.auth.auth.AuthDtos.AuthResponse;
import com.enterprise.auth.auth.AuthDtos.LoginRequest;
import com.enterprise.auth.auth.AuthDtos.RegisterRequest;
import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.user.Role;
import com.enterprise.auth.user.SellerProfile;
import com.enterprise.auth.user.SellerProfileRepository;
import com.enterprise.auth.user.User;
import com.enterprise.auth.user.UserRepository;
import com.enterprise.auth.user.UserResponse;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up and sign-in. Customers and sellers use the marketplace website; the platform administrator signs
 * in only through the admin console ({@link #adminLogin}). Every attempt is written to the login audit trail.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID = "Invalid username or password";

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAuditService audit;

    public AuthService(UserRepository userRepository, SellerProfileRepository sellerRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService, LoginAuditService audit) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.audit = audit;
    }

    /** Creates a CUSTOMER or SELLER account (sellers also get a store profile) and signs it in. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        boolean seller = request.accountType() == AccountType.SELLER;
        String storeName = trim(request.storeName());
        if (seller) {
            if (storeName == null || trim(request.phone()) == null || trim(request.city()) == null) {
                throw ApiException.badRequest("Store name, business phone and city are required for seller accounts");
            }
            if (sellerRepository.existsByStoreNameIgnoreCase(storeName)) {
                throw ApiException.conflict("A store named '" + storeName + "' already exists");
            }
        }
        User user = userRepository.save(new User(username, email, passwordEncoder.encode(request.password()),
                trim(request.fullName()), trim(request.phone()), seller ? Role.SELLER : Role.CUSTOMER));
        SellerProfile store = null;
        if (seller) {
            store = sellerRepository.save(new SellerProfile(user, storeName, trim(request.storeDescription()),
                    trim(request.phone()), trim(request.city()), "United Arab Emirates",
                    trim(request.tradeLicenseNumber()), trim(request.vatNumber())));
        }
        user.recordLogin();
        log.info("Registered new {} {} (id={})", user.getRole(), user.getUsername(), user.getId());
        return toResponse(user, store);
    }

    /**
     * Marketplace sign-in (customer or seller) with username or e-mail. One generic error is used for every
     * credential problem - including the admin account, which does not exist as far as the marketplace knows.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        Role portal = request.accountType() == AccountType.SELLER ? Role.SELLER : Role.CUSTOMER;
        User user = authenticate(request, portal, client);
        if (user.isAdmin()) {
            fail(user, request, portal, client, "Admin account used on the marketplace sign-in");
            throw ApiException.unauthorized(INVALID);
        }
        if (request.accountType() == AccountType.CUSTOMER && user.isSeller()) {
            fail(user, request, portal, client, "Seller account on the customer sign-in");
            throw ApiException.forbidden("This is a seller account. Please sign in to Seller Central.");
        }
        if (request.accountType() == AccountType.SELLER && !user.isSeller()) {
            fail(user, request, portal, client, "Customer account on the seller sign-in");
            throw ApiException.forbidden("This is a customer account. Please use the customer sign-in.");
        }
        return succeed(user, request, portal, client);
    }

    /** Admin console sign-in: only the platform administrator account is accepted. */
    @Transactional
    public AuthResponse adminLogin(LoginRequest request, ClientInfo client) {
        User user = authenticate(request, Role.ADMIN, client);
        if (!user.isAdmin()) {
            fail(user, request, Role.ADMIN, client, "Non-admin account on the admin console");
            throw ApiException.unauthorized(INVALID);
        }
        return succeed(user, request, Role.ADMIN, client);
    }

    private User authenticate(LoginRequest request, Role portal, ClientInfo client) {
        String login = request.username().trim();
        Optional<User> found = login.contains("@")
                ? userRepository.findByEmailIgnoreCase(login)
                : userRepository.findByUsernameIgnoreCase(login);
        if (found.isEmpty()) {
            record(null, login, portal, false, "Unknown username", client);
            throw ApiException.unauthorized(INVALID);
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            record(user.getId(), login, portal, false, "Wrong password", client);
            throw ApiException.unauthorized(INVALID);
        }
        if (!user.isEnabled()) {
            record(user.getId(), login, portal, false, "Account suspended", client);
            throw ApiException.forbidden("This account has been suspended. Please contact Nexora support.");
        }
        return user;
    }

    private AuthResponse succeed(User user, LoginRequest request, Role portal, ClientInfo client) {
        user.recordLogin();
        record(user.getId(), request.username().trim(), portal, true, null, client);
        log.info("{} {} signed in", user.getRole(), user.getUsername());
        return toResponse(user, sellerRepository.findByUserId(user.getId()).orElse(null));
    }

    private void fail(User user, LoginRequest request, Role portal, ClientInfo client, String reason) {
        record(user.getId(), request.username().trim(), portal, false, reason, client);
    }

    /** The audit trail is written in its own transaction; a broken audit write never blocks a sign-in. */
    private void record(Long userId, String username, Role portal, boolean success, String reason, ClientInfo client) {
        try {
            audit.record(userId, username, portal, success, reason, client);
        } catch (RuntimeException ex) {
            log.warn("Could not record the login event of {}: {}", username, ex.getMessage());
        }
    }

    private AuthResponse toResponse(User user, SellerProfile store) {
        TokenService.IssuedToken token = tokenService.issue(user, store == null ? null : store.getStoreName());
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user, store));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
