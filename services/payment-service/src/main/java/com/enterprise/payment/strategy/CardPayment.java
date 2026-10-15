package com.enterprise.payment.strategy;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.processor.PaymentProcessor;
import com.enterprise.payment.processor.PaymentRequest;
import com.enterprise.payment.processor.PaymentResult;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/** Debit/credit card: Luhn checksum, holder, expiry (MM/YY) and CVV are checked before the provider is called. */
@Component
public class CardPayment implements PaymentStrategy {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public void validate(PaymentDetails details) {
        String number = digits(details.cardNumber());
        if (!number.matches("\\d{13,19}") || !passesLuhn(number)) {
            throw ApiException.badRequest("The card number is not valid");
        }
        if (details.cardHolder() == null || details.cardHolder().isBlank()) {
            throw ApiException.badRequest("The card holder name is required");
        }
        String expiry = details.expiry() == null ? "" : details.expiry().trim();
        if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw ApiException.badRequest("The expiry date must be in MM/YY format");
        }
        YearMonth value = YearMonth.of(2000 + Integer.parseInt(expiry.substring(3)),
                Integer.parseInt(expiry.substring(0, 2)));
        if (value.isBefore(YearMonth.now())) {
            throw ApiException.badRequest("The card has expired");
        }
        if (details.cvv() == null || !details.cvv().matches("\\d{3,4}")) {
            throw ApiException.badRequest("The CVV must be 3 or 4 digits");
        }
    }

    @Override
    public PaymentResult pay(String orderNumber, BigDecimal amount, String currency, PaymentDetails details,
                             PaymentProcessor processor) {
        String number = digits(details.cardNumber());
        String masked = brand(number) + " **** " + number.substring(number.length() - 4);
        return processor.process(new PaymentRequest(orderNumber, amount, currency, PaymentMethod.CARD, number, masked));
    }

    static String digits(String cardNumber) {
        return cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
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

    static String brand(String number) {
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
