package com.enterprise.payment.processor;

import com.enterprise.payment.payment.PaymentMethod;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated payment provider with predictable test data, like the sandbox of a real gateway:
 * <ul>
 *   <li>cards ending 0002 are declined, 9995 have insufficient funds, payments above AED 50,000 exceed the limit</li>
 *   <li>UPI IDs starting with "fail" are declined by the payer's bank</li>
 *   <li>everything else succeeds</li>
 * </ul>
 */
@Component
public class MockPaymentProcessor implements PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProcessor.class);
    static final BigDecimal CARD_LIMIT = new BigDecimal("50000.00");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public PaymentResult process(PaymentRequest request) {
        String reference = References.next("MOCK");
        String instrument = request.instrument() == null ? "" : request.instrument();
        log.debug("Mock provider processing {}", request);
        if (request.method() == PaymentMethod.CARD) {
            if (instrument.endsWith("0002")) {
                return PaymentResult.failed(reference, request.maskedInstrument(), "Card declined by the issuing bank.");
            }
            if (instrument.endsWith("9995")) {
                return PaymentResult.failed(reference, request.maskedInstrument(), "Insufficient funds.");
            }
            if (request.amount().compareTo(CARD_LIMIT) > 0) {
                return PaymentResult.failed(reference, request.maskedInstrument(), "Amount exceeds the card limit.");
            }
        }
        if (request.method() == PaymentMethod.UPI && instrument.startsWith("fail")) {
            return PaymentResult.failed(reference, request.maskedInstrument(),
                    "The payment was declined by the payer's bank.");
        }
        return PaymentResult.success(reference, request.maskedInstrument());
    }
}
