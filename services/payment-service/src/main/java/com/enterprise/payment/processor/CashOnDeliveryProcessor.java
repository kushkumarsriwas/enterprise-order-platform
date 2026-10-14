package com.enterprise.payment.processor;

import com.enterprise.payment.payment.PaymentMethod;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Nothing is charged now: the payment stays PENDING until the courier collects the cash. */
@Component
public class CashOnDeliveryProcessor implements PaymentProcessor {

    static final BigDecimal COD_LIMIT = new BigDecimal("5000.00");

    @Override
    public PaymentMethod method() {
        return PaymentMethod.COD;
    }

    @Override
    public ProcessorResult process(PaymentCommand command) {
        String reference = References.next("COD");
        if (command.amount().compareTo(COD_LIMIT) > 0) {
            return ProcessorResult.failed(reference, "Cash on delivery",
                    "Cash on delivery is only available for orders up to " + command.currency() + " " + COD_LIMIT + ".");
        }
        return ProcessorResult.pending(reference, "Cash on delivery");
    }
}
