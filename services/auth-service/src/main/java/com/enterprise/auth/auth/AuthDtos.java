package com.enterprise.auth.auth;

import com.enterprise.auth.user.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Which side of the marketplace the user signs up / signs in to. */
    public enum AccountType {
        CUSTOMER,
        SELLER
    }

    public record RegisterRequest(
            @NotNull AccountType accountType,
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "may contain letters, digits, '.', '_' and '-' only")
            String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String password,
            @Size(max = 120) String fullName,
            @Size(max = 30) @Pattern(regexp = "^$|^[+0-9 ()-]{7,30}$", message = "is not a valid phone number") String phone,
            @Size(max = 80) String storeName,
            @Size(max = 500) String storeDescription,
            @Size(max = 60) String city,
            @Size(max = 40) String tradeLicenseNumber,
            @Pattern(regexp = "^$|^\\d{15}$", message = "must be the 15-digit TRN") String vatNumber) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password, AccountType accountType) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
