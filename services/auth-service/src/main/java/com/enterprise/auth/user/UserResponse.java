package com.enterprise.auth.user;

import java.time.Instant;

public record UserResponse(Long id, String username, String email, String fullName, String role, boolean enabled,
                           Instant createdAt, Instant lastLoginAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
