package com.enterprise.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.auth.AuthDtos.LoginRequest;
import com.enterprise.auth.auth.AuthDtos.RegisterRequest;
import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.user.Role;
import com.enterprise.auth.user.User;
import com.enterprise.auth.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesCustomerWithHashedPassword() {
        when(userRepository.existsByUsernameIgnoreCase("kush")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("kush@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(User.class))).thenReturn(new TokenService.IssuedToken("jwt", 3600));

        var response = authService.register(new RegisterRequest("kush", "Kush@Example.com", "Secret123", "Kush"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().role()).isEqualTo("CUSTOMER");
        assertThat(response.user().email()).isEqualTo("kush@example.com");
        verify(passwordEncoder).encode("Secret123");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("kush")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("kush", "k@example.com", "Secret123", null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorized() {
        User user = new User("kush", "kush@example.com", "$2a$hash", null, Role.CUSTOMER);
        when(userRepository.findByUsernameIgnoreCase("kush")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kush", "wrong")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void disabledAccountCannotLogIn() {
        User user = new User("kush", "kush@example.com", "$2a$hash", null, Role.CUSTOMER);
        user.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("kush@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kush@example.com", "Secret123")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
