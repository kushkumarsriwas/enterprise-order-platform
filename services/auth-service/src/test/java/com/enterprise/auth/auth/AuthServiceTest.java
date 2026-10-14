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
import com.enterprise.auth.user.SellerProfile;
import com.enterprise.auth.user.SellerProfileRepository;
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
    private SellerProfileRepository sellerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private AuthService authService;

    private static RegisterRequest customer(String username, String email) {
        return new RegisterRequest(AccountType.CUSTOMER, username, email, "Secret123", "Kush", null, null, null, null, null, null);
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
    void sellerRegistrationCreatesAStoreAndPutsItInTheToken() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sellerRepository.save(any(SellerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(User.class), eq("Gulf Gadgets"))).thenReturn(new TokenService.IssuedToken("jwt", 3600));

        var response = authService.register(new RegisterRequest(AccountType.SELLER, "gulfgadgets", "sales@gulf.ae",
                "Secret123", "Aisha", "+971 50 123 4567", "Gulf Gadgets", "Phones and accessories", "Dubai", "DED-1234",
                "100123456700003"));

        assertThat(response.user().role()).isEqualTo("SELLER");
        assertThat(response.user().store().storeName()).isEqualTo("Gulf Gadgets");
        assertThat(response.user().store().vatNumber()).isEqualTo("100123456700003");
    }

    @Test
    void sellerRegistrationNeedsStoreDetails() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest(AccountType.SELLER, "shop", "s@x.ae",
                "Secret123", null, null, null, null, null, null, null)))
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
    void customersCannotUseTheSellerPortal() {
        User user = new User("kush", "kush@example.com", "$2a$hash", null, null, Role.CUSTOMER);
        when(userRepository.findByUsernameIgnoreCase("kush")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("kush", "Secret123", AccountType.SELLER)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("customer sign-in");
    }

    @Test
    void sellersSignInToSellerCentralOnly() {
        User user = new User("gulf", "sales@gulf.ae", "$2a$hash", null, null, Role.SELLER);
        when(userRepository.findByEmailIgnoreCase("sales@gulf.ae")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("sales@gulf.ae", "Secret123", AccountType.CUSTOMER)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
