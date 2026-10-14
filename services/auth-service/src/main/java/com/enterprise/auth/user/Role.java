package com.enterprise.auth.user;

/** The marketplace has exactly two kinds of accounts. */
public enum Role {
    /** Shops on the marketplace: search, cart, checkout, orders, reviews. */
    CUSTOMER,
    /** Runs a store: inventory management, purchasing, sales, finance and analytics (Seller Central). */
    SELLER
}
