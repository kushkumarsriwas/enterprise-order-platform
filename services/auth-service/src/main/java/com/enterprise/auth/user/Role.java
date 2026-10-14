package com.enterprise.auth.user;

/**
 * Customers and sellers use the marketplace website. The single ADMIN account belongs to the platform owner:
 * it is created from configuration (never through sign-up) and signs in only through the separate admin console.
 */
public enum Role {
    /** Shops on the marketplace: search, cart, checkout, orders, reviews. */
    CUSTOMER,
    /** Runs a store: inventory management, purchasing, sales, finance and analytics (Seller Central). */
    SELLER,
    /** Platform owner: sees and oversees everything from the admin console (Nexora Control Center). */
    ADMIN
}
