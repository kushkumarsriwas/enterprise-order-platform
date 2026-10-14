package com.enterprise.payment.processor;

import java.util.Locale;
import java.util.UUID;

final class References {

    private References() {
    }

    static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
