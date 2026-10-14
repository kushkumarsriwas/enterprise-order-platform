package com.enterprise.order.order;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Human-friendly, non-guessable order numbers like ORD-261014-7KQ2ZD. */
public final class OrderNumberGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private OrderNumberGenerator() {
    }

    public static String next() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return "ORD-" + LocalDate.now(ZoneOffset.UTC).format(DATE) + "-" + suffix;
    }
}
