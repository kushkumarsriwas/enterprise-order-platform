package com.enterprise.auth.auth;

import com.enterprise.auth.auth.AuthDtos.AccountType;
import com.enterprise.auth.auth.AuthDtos.AuthResponse;
import com.enterprise.auth.auth.AuthDtos.LoginRequest;
import com.enterprise.auth.auth.AuthDtos.RegisterRequest;
import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.user.MerchantProfile;
import com.enterprise.auth.user.MerchantProfileRepository;
import com.enterprise.auth.user.Role;
import com.enterprise.auth.user.User;
import com.enterprise.auth.user.UserRepository;
import com.enterprise.auth.user.UserResponse;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, MerchantProfileRepository merchantRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** Creates a CUSTOMER or MERCHANT account and logs it in. Admins are seeded, never self-registered. */
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
        boolean merchant = request.accountType() == AccountType.MERCHANT;
        String storeName = trim(request.storeName());
        if (merchant) {
            if (storeName == null || trim(request.phone()) == null || trim(request.city()) == null) {
                throw ApiException.badRequest("Store name, business phone and city are required for merchant accounts");
            }
            if (merchantRepository.existsByStoreNameIgnoreCase(storeName)) {
                throw ApiException.conflict("A store named '" + storeName + "' already exists");
            }
        }
        User user = userRepository.save(new User(username, email, passwordEncoder.encode(request.password()),
                trim(request.fullName()), trim(request.phone()), merchant ? Role.MERCHANT : Role.CUSTOMER));
        MerchantProfile profile = null;
        if (merchant) {
            profile = merchantRepository.save(new MerchantProfile(user, storeName, trim(request.storeDescription()),
                    trim(request.phone()), trim(request.city()), "United Arab Emirates",
                    trim(request.tradeLicenseNumber())));
        }
        user.recordLogin();
        log.info("Registered new {} {} (id={})", user.getRole(), user.getUsername(), user.getId());
        return toResponse(user, profile);
    }

    /**
     * Accepts username or email. The portal (customer / business) must match the account type, and one
     * generic error message is used for wrong credentials so attackers can't probe for accounts.
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
            throw ApiException.forbidden("This account has been suspended. Please contact support.");
        }
        if (request.accountType() == AccountType.CUSTOMER && user.getRole() != Role.CUSTOMER) {
            throw ApiException.forbidden("This is a business account. Please use the Merchant login.");
        }
        if (request.accountType() == AccountType.MERCHANT && user.getRole() == Role.CUSTOMER) {
            throw ApiException.forbidden("This is a customer account. Please use the Customer login.");
        }
        user.recordLogin();
        log.info("{} {} logged in", user.getRole(), user.getUsername());
        return toResponse(user, merchantRepository.findByUserId(user.getId()).orElse(null));
    }

    private AuthResponse toResponse(User user, MerchantProfile profile) {
        TokenService.IssuedToken token = tokenService.issue(user, profile == null ? null : profile.getStoreName());
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user, profile));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
