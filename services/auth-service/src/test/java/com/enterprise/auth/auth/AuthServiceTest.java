package com.enterprise.auth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.auth.AuthDtos.AccountType;
import com.enterprise.auth.auth.AuthDtos.LoginRequest;
import com.enterprise.auth.auth.AuthDtos.RegisterRequest;
import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.user.MerchantProfile;
import com.enterprise.auth.user.MerchantProfileRepository;
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
    private MerchantProfileRepository merchantRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private AuthService authService;

    private static RegisterRequest customer(String username, String email) {
        return new RegisterRequest(AccountType.CUSTOMER, username, email, "Secret123", "Kush", null, null, null, null, null);
    }

    @Test
    void registerCreatesCustomerWithHashedPassword() {
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(User.class), isNull())).thenReturn(new TokenService.IssuedToken("jwt", 3600));

        var response = authService.register(customer("kush", "Kush@Example.com"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().role()).isEqualTo("CUSTOMER");
        assertThat(response.user().email()).isEqualTo("kush@example.com");
        assertThat(response.user().store()).isNull();
    }

    @Test
    void merchantRegistrationCreatesAStoreAndPutsItInTheToken() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantRepository.save(any(MerchantProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(User.class), eq("Gulf Gadgets"))).thenReturn(new TokenService.IssuedToken("jwt", 3600));

        var response = authService.register(new RegisterRequest(AccountType.MERCHANT, "gulfgadgets", "sales@gulf.ae",
                "Secret123", "Aisha", "+971 50 123 4567", "Gulf Gadgets", "Phones and accessories", "Dubai", "DED-1234"));

        assertThat(response.user().role()).isEqualTo("MERCHANT");
        assertThat(response.user().store().storeName()).isEqualTo("Gulf Gadgets");
    }

    @Test
    void merchantRegistrationNeedsStoreDetails() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest(AccountType.MERCHANT, "shop", "s@x.ae",
                "Secret123", null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Store name");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("kush")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(customer("kush", "k@example.com")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorized() {
        User user = new User("kush", "kush@example.com", "$2a$hash", null, null, Role.CUSTOMER);
        when(userRepository.findByUsernameIgnoreCase("kush")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kush", "wrong", AccountType.CUSTOMER)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void customersCannotUseTheMerchantPortal() {
        User user = new User("kush", "kush@example.com", "$2a$hash", null, null, Role.CUSTOMER);
        when(userRepository.findByUsernameIgnoreCase("kush")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kush", "Secret123", AccountType.MERCHANT)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Customer login");
    }

    @Test
    void suspendedAccountCannotLogIn() {
        User user = new User("kush", "kush@example.com", "$2a$hash", null, null, Role.CUSTOMER);
        user.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("kush@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kush@example.com", "Secret123", null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
