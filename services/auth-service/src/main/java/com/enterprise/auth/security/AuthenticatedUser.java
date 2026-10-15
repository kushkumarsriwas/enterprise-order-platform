package com.enterprise.auth.security;

/** The caller identity extracted from a verified JWT. storeName is set for seller accounts. */
public record AuthenticatedUser(Long id, String username, String email, String role, String storeName) {

    public boolean isSeller() {
        return "SELLER".equals(role);
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    /** Name shown to other users: the store name for sellers, otherwise the username. */
    public String displayName() {
        return storeName != null && !storeName.isBlank() ? storeName : username;
    }
}
