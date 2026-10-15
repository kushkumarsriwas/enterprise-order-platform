package com.enterprise.auth.common;

import java.util.Locale;

/** Builds safe LIKE patterns: '%' and '_' typed by a user are matched literally, not as wildcards. */
public final class Like {

    private Like() {
    }

    /** "50%_off" -> "%50\%\_off%" (use with {@code like :pattern escape '\'}). */
    public static String contains(String text) {
        String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
