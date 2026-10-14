package com.enterprise.auth.user;

import java.time.Instant;

public record UserResponse(Long id, String username, String email, String fullName, String phone, String role,
                           boolean enabled, Instant createdAt, Instant lastLoginAt, StoreResponse store) {

    public static UserResponse from(User user, MerchantProfile profile) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRole().name(), user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt(),
                profile == null ? null : StoreResponse.from(profile));
    }

    /** Public information about a merchant's store. */
    public record StoreResponse(Long merchantId, String storeName, String description, String phone, String city,
                                String country, String tradeLicenseNumber, Instant memberSince) {

        public static StoreResponse from(MerchantProfile profile) {
            return new StoreResponse(profile.getUserId(), profile.getStoreName(), profile.getDescription(),
                    profile.getPhone(), profile.getCity(), profile.getCountry(), profile.getTradeLicenseNumber(),
                    profile.getUser().getCreatedAt());
        }
    }
}
