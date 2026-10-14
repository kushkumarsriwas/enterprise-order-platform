package com.enterprise.auth.user;

import com.enterprise.auth.common.ApiException;
import com.enterprise.auth.user.UserDtos.AddressRequest;
import com.enterprise.auth.user.UserDtos.PasswordChange;
import com.enterprise.auth.user.UserDtos.ProfileUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The logged-in user's own account: profile, store details, delivery address and password. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, SellerProfileRepository sellerRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        User user = load(userId);
        return UserResponse.from(user, sellerRepository.findByUserId(userId).orElse(null));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdate request) {
        User user = load(userId);
        user.updateContact(trim(request.fullName()), trim(request.phone()));
        SellerProfile store = sellerRepository.findByUserId(userId).orElse(null);
        if (store != null) {
            store.update(trim(request.storeDescription()),
                    trim(request.phone()) == null ? store.getPhone() : trim(request.phone()),
                    trim(request.city()) == null ? store.getCity() : trim(request.city()),
                    trim(request.supportEmail()) == null ? store.getSupportEmail() : trim(request.supportEmail()),
                    trim(request.vatNumber()));
        }
        log.info("User {} updated the profile", user.getUsername());
        return UserResponse.from(user, store);
    }

    @Transactional
    public UserResponse updateAddress(Long userId, AddressRequest request) {
        User user = load(userId);
        user.changeAddress(new Address(trim(request.recipient()), trim(request.phone()), trim(request.line1()),
                trim(request.line2()), trim(request.city()), trim(request.region()),
                trim(request.country()) == null ? "United Arab Emirates" : trim(request.country())));
        return UserResponse.from(user, sellerRepository.findByUserId(userId).orElse(null));
    }

    /** The current password must be confirmed; the new one is hashed with BCrypt before it is stored. */
    @Transactional
    public void changePassword(Long userId, PasswordChange request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("The current password is not correct");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("The new password must be different from the current one");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("User {} changed the password", user.getUsername());
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
