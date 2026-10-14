package com.enterprise.payment.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {

    private Money() {
    }

    static BigDecimal of(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }

    /** part / whole as a percentage with one decimal; 0 when whole is 0. */
    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    static String format(BigDecimal amount) {
        return String.format("AED %,.2f", amount);
    }
}
