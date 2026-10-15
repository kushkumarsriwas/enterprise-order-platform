package com.enterprise.notification.security;

/** The caller identity extracted from a verified JWT. storeName is set for seller accounts. */
public record AuthenticatedUser(Long id, String username, String email, String role, String storeName) {

    /** Another Nexora service calling an internal endpoint without a user (scheduled jobs). */
    public static final AuthenticatedUser SERVICE = new AuthenticatedUser(0L, "system", null, "SERVICE", null);

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isSeller() {
        return "SELLER".equals(role);
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    public boolean isService() {
        return "SERVICE".equals(role);
    }

    /** Name shown to other users: the store name for sellers, otherwise the username. */
    public String displayName() {
        return storeName != null && !storeName.isBlank() ? storeName : username;
    }
}
