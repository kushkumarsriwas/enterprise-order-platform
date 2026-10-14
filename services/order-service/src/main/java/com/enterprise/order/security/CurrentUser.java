package com.enterprise.order.security;

import com.enterprise.order.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helpers to read the authenticated caller and the raw bearer token (for token relay). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw ApiException.unauthorized("Authentication required");
    }

    /** Returns the bearer token of the current request, or null when called outside a request. */
    public static String token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token) {
            return token;
        }
        return null;
    }
}
