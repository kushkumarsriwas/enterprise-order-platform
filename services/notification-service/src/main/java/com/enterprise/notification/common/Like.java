package com.enterprise.notification.common;

import java.util.Locale;

/** Builds safe LIKE patterns: '%' and '_' typed by a user are matched literally, not as wildcards. */
public final class Like {

    /** Use as: {@code cb.like(cb.lower(path), Like.contains(text), Like.ESCAPE)}. */
    public static final char ESCAPE = '\\';

    private Like() {
    }

    public static String contains(String text) {
        return "%" + escape(text) + "%";
    }

    public static String startsWith(String text) {
        return escape(text) + "%";
    }

    private static String escape(String text) {
        String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
