package com.enterprise.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.user.UserDtos.AddressRequest;
import com.enterprise.auth.user.UserDtos.PasswordChange;
import com.enterprise.auth.user.UserDtos.ProfileUpdate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SellerProfileRepository sellerRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserService userService;
    private User customer;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, sellerRepository, passwordEncoder);
        customer = new User("sara", "sara@example.com", passwordEncoder.encode("Current1"), "Sara", null, Role.CUSTOMER);
    }

    @Test
    void passwordChangeNeedsTheCurrentPassword() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> userService.changePassword(7L, new PasswordChange("wrong", "Brand-new1")))
                .isInstanceOf(ApiException.class)
                .hasMessage("The current password is not correct");
        assertThat(passwordEncoder.matches("Current1", customer.getPasswordHash())).isTrue();
    }

    @Test
    void newPasswordIsStoredAsABcryptHash() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));

        userService.changePassword(7L, new PasswordChange("Current1", "Brand-new1"));

        assertThat(customer.getPasswordHash()).startsWith("$2a$").doesNotContain("Brand-new1");
        assertThat(passwordEncoder.matches("Brand-new1", customer.getPasswordHash())).isTrue();
        assertThat(customer.getPasswordChangedAt()).isNotNull();
    }

    @Test
    void reusingTheSamePasswordIsRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> userService.changePassword(7L, new PasswordChange("Current1", "Current1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different");
    }

    @Test
    void addressDefaultsToTheUae() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(sellerRepository.findByUserId(7L)).thenReturn(Optional.empty());

        UserResponse response = userService.updateAddress(7L, new AddressRequest("Sara Khan", "+971501234567",
                "Villa 12, Street 4", null, "Abu Dhabi", "Abu Dhabi", null));

        assertThat(response.address().country()).isEqualTo("United Arab Emirates");
        assertThat(response.address().formatted())
                .isEqualTo("Sara Khan, Villa 12, Street 4, Abu Dhabi, Abu Dhabi, United Arab Emirates (+971501234567)");
    }

    @Test
    void sellerProfileUpdateKeepsTheStoreNameAndPhoneWhenBlank() {
        User sellerUser = new User("gulf", "sales@gulf.ae", "hash", "Aisha", "+971501110000", Role.SELLER);
        SellerProfile store = new SellerProfile(sellerUser, "Gulf Gadgets", "Phones", "+971501110000", "Dubai",
                "United Arab Emirates", null, null);
        when(userRepository.findById(9L)).thenReturn(Optional.of(sellerUser));
        when(sellerRepository.findByUserId(9L)).thenReturn(Optional.of(store));

        UserResponse response = userService.updateProfile(9L, new ProfileUpdate("Aisha K", " ", "Phones & tablets",
                null, "care@gulf.ae", "100123456700003"));

        assertThat(response.store().storeName()).isEqualTo("Gulf Gadgets");
        assertThat(response.store().phone()).isEqualTo("+971501110000");
        assertThat(response.store().description()).isEqualTo("Phones & tablets");
        assertThat(response.store().supportEmail()).isEqualTo("care@gulf.ae");
        assertThat(response.store().vatNumber()).isEqualTo("100123456700003");
    }
}
