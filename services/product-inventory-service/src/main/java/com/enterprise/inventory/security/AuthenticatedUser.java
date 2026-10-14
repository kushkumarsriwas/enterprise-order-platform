package com.enterprise.inventory.security;

/** The caller identity extracted from a verified JWT. */
public record AuthenticatedUser(Long id, String username, String email, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
