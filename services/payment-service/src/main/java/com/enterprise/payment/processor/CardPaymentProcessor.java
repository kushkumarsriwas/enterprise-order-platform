package com.enterprise.payment.processor;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/**
 * Mock card acquirer. Validates the card (Luhn checksum, expiry, CVV) and simulates issuer decisions:
 * cards ending 0002 are declined, 9995 have insufficient funds, everything else is approved.
 */
@Component
public class CardPaymentProcessor implements PaymentProcessor {

    private static final BigDecimal CARD_LIMIT = new BigDecimal("50000.00");

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public ProcessorResult process(PaymentCommand command) {
        String number = command.cardNumber() == null ? "" : command.cardNumber().replaceAll("[\\s-]", "");
        if (!number.matches("\\d{13,19}") || !passesLuhn(number)) {
            throw ApiException.badRequest("The card number is not valid");
        }
        if (command.cardHolder() == null || command.cardHolder().isBlank()) {
            throw ApiException.badRequest("The card holder name is required");
        }
        validateExpiry(command.expiry());
        if (command.cvv() == null || !command.cvv().matches("\\d{3,4}")) {
            throw ApiException.badRequest("The CVV must be 3 or 4 digits");
        }
        String last4 = number.substring(number.length() - 4);
        String masked = brand(number) + " **** " + last4;
        String reference = References.next("CARD");
        if (last4.equals("0002")) {
            return ProcessorResult.failed(reference, masked, "Card declined by the issuing bank.");
        }
        if (last4.equals("9995")) {
            return ProcessorResult.failed(reference, masked, "Insufficient funds.");
        }
        if (command.amount().compareTo(CARD_LIMIT) > 0) {
            return ProcessorResult.failed(reference, masked, "Amount exceeds the card limit.");
        }
        return ProcessorResult.success(reference, masked);
    }

    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private static void validateExpiry(String expiry) {
        if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw ApiException.badRequest("The expiry date must be in MM/YY format");
        }
        YearMonth value = YearMonth.of(2000 + Integer.parseInt(expiry.substring(3)), Integer.parseInt(expiry.substring(0, 2)));
        if (value.isBefore(YearMonth.now())) {
            throw ApiException.badRequest("The card has expired");
        }
    }

    private static String brand(String number) {
        if (number.startsWith("4")) {
            return "VISA";
        }
        if (number.matches("5[1-5].*|2[2-7].*")) {
            return "MASTERCARD";
        }
        if (number.matches("3[47].*")) {
            return "AMEX";
        }
        return "CARD";
    }
}
