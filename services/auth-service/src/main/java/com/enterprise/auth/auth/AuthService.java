package com.enterprise.auth.auth;

import com.enterprise.auth.auth.AuthDtos.AuthResponse;
import com.enterprise.auth.auth.AuthDtos.LoginRequest;
import com.enterprise.auth.auth.AuthDtos.RegisterRequest;
import com.enterprise.auth.common.ApiException;
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
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** Creates a CUSTOMER account and logs it in. Admins are seeded, never self-registered. */
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
        String fullName = request.fullName() == null || request.fullName().isBlank() ? null : request.fullName().trim();
        User user = userRepository.save(new User(username, email, passwordEncoder.encode(request.password()),
                fullName, Role.CUSTOMER));
        user.recordLogin();
        log.info("Registered new customer {} (id={})", user.getUsername(), user.getId());
        return toResponse(user);
    }

    /** Accepts username or email. Uses one generic error message so attackers can't probe for accounts. */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String login = request.username().trim();
        User user = (login.contains("@")
                ? userRepository.findByEmailIgnoreCase(login)
                : userRepository.findByUsernameIgnoreCase(login))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid username or password"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account is disabled");
        }
        user.recordLogin();
        log.info("User {} logged in", user.getUsername());
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }
}
