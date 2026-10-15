package com.enterprise.auth.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    static final String PHONE = "^$|^[+0-9 ()-]{7,30}$";

    private UserDtos() {
    }

    public record ProfileUpdate(@Size(max = 120) String fullName,
                                @Size(max = 30) @Pattern(regexp = PHONE, message = "is not a valid phone number") String phone,
                                @Size(max = 500) String storeDescription,
                                @Size(max = 60) String city,
                                @Email @Size(max = 120) String supportEmail,
                                @Pattern(regexp = "^$|^\\d{15}$", message = "must be the 15-digit TRN") String vatNumber) {
    }

    public record AddressRequest(@NotBlank @Size(max = 120) String recipient,
                                 @NotBlank @Size(max = 30) @Pattern(regexp = PHONE, message = "is not a valid phone number")
                                 String phone,
                                 @NotBlank @Size(max = 150) String line1,
                                 @Size(max = 150) String line2,
                                 @NotBlank @Size(max = 60) String city,
                                 @Size(max = 60) String region,
                                 @Size(max = 60) String country) {
    }

    public record PasswordChange(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 8, max = 72)
                                 @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                                         message = "must contain at least one letter and one digit")
                                 String newPassword) {
    }
}
