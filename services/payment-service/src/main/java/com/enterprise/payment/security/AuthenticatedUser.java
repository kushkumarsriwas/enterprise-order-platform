package com.enterprise.payment.security;

/** The caller identity extracted from a verified JWT. storeName is set for merchant accounts. */
public record AuthenticatedUser(Long id, String username, String email, String role, String storeName) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isMerchant() {
        return "MERCHANT".equals(role);
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    /** Name shown to other users: the store name for merchants, otherwise the username. */
    public String displayName() {
        return storeName != null && !storeName.isBlank() ? storeName : username;
    }
}
