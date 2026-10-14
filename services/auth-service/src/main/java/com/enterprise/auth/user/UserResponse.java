package com.enterprise.auth.user;

import java.time.Instant;

public record UserResponse(Long id, String username, String email, String fullName, String phone, String role,
                           Instant createdAt, Instant lastLoginAt, AddressResponse address, StoreResponse store) {

    public static UserResponse from(User user, SellerProfile profile) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRole().name(), user.getCreatedAt(), user.getLastLoginAt(),
                AddressResponse.from(user.getAddress()), profile == null ? null : StoreResponse.from(profile));
    }

    public record AddressResponse(String recipient, String phone, String line1, String line2, String city,
                                  String region, String country, String formatted) {

        static AddressResponse from(Address address) {
            if (address == null || address.getLine1() == null) {
                return null;
            }
            return new AddressResponse(address.getRecipient(), address.getPhone(), address.getLine1(),
                    address.getLine2(), address.getCity(), address.getRegion(), address.getCountry(),
                    address.formatted());
        }
    }

    /** Public information about a seller's store. */
    public record StoreResponse(Long sellerId, String storeName, String description, String phone,
                                String supportEmail, String city, String country, String tradeLicenseNumber,
                                String vatNumber, Instant memberSince) {

        public static StoreResponse from(SellerProfile profile) {
            return new StoreResponse(profile.getUserId(), profile.getStoreName(), profile.getDescription(),
                    profile.getPhone(), profile.getSupportEmail(), profile.getCity(), profile.getCountry(),
                    profile.getTradeLicenseNumber(), profile.getVatNumber(), profile.getUser().getCreatedAt());
        }
    }
}
