package com.enterprise.payment.processor;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Mock UPI / instant-payment collect request. Payer addresses starting with "fail" are declined. */
@Component
public class UpiPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public ProcessorResult process(PaymentCommand command) {
        String upiId = command.upiId() == null ? "" : command.upiId().trim().toLowerCase(Locale.ROOT);
        if (!upiId.matches("^[a-z0-9._-]{2,64}@[a-z]{2,32}$")) {
            throw ApiException.badRequest("The UPI ID must look like name@bank");
        }
        String reference = References.next("UPI");
        if (upiId.startsWith("fail")) {
            return ProcessorResult.failed(reference, upiId, "The payment was declined by the payer's bank.");
        }
        return ProcessorResult.success(reference, upiId);
    }
}
