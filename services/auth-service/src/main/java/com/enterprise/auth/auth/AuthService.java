package com.enterprise.auth.auth;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-up and sign-in for the two account types of the marketplace: customers and sellers. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, SellerProfileRepository sellerRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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
     * Accepts username or email. The side of the marketplace (customer / seller) must match the account,
     * and one generic error message is used for wrong credentials so attackers can't probe for accounts.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String login = request.username().trim();
        User user = (login.contains("@")
                ? userRepository.findByEmailIgnoreCase(login)
                : userRepository.findByUsernameIgnoreCase(login))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid username or password"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account has been closed.");
        }
        if (request.accountType() == AccountType.CUSTOMER && user.isSeller()) {
            throw ApiException.forbidden("This is a seller account. Please sign in to Seller Central.");
        }
        if (request.accountType() == AccountType.SELLER && !user.isSeller()) {
            throw ApiException.forbidden("This is a customer account. Please use the customer sign-in.");
        }
        user.recordLogin();
        log.info("{} {} signed in", user.getRole(), user.getUsername());
        return toResponse(user, sellerRepository.findByUserId(user.getId()).orElse(null));
    }

    private AuthResponse toResponse(User user, SellerProfile store) {
        TokenService.IssuedToken token = tokenService.issue(user, store == null ? null : store.getStoreName());
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user, store));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
