package com.enterprise.payment.processor;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory pattern: picks the processor for a method. Spring injects every PaymentProcessor bean. */
@Component
public class PaymentProcessorFactory {

    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    public PaymentProcessorFactory(List<PaymentProcessor> available) {
        available.forEach(processor -> processors.put(processor.method(), processor));
    }

    public PaymentProcessor forMethod(PaymentMethod method) {
        PaymentProcessor processor = processors.get(method);
        if (processor == null) {
            throw ApiException.badRequest("Payment method " + method + " is not supported");
        }
        return processor;
    }
}
