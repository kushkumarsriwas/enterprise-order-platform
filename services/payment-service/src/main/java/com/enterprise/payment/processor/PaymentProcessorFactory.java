package com.enterprise.payment.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory pattern: creates (selects) the payment processor configured with {@code app.payments.provider}.
 * Spring injects every {@link PaymentProcessor} bean; the factory hides which one is used.
 */
@Component
public class PaymentProcessorFactory {

    private final Map<String, PaymentProcessor> processors;
    private final String configured;

    public PaymentProcessorFactory(List<PaymentProcessor> available,
                                   @Value("${app.payments.provider:mock}") String configured) {
        this.processors = available.stream().collect(Collectors.toMap(PaymentProcessor::name, Function.identity()));
        this.configured = configured;
        if (!processors.containsKey(configured)) {
            throw new IllegalStateException("Unknown payment provider '" + configured + "', expected one of "
                    + processors.keySet());
        }
    }

    public PaymentProcessor processor() {
        return processors.get(configured);
    }
}
