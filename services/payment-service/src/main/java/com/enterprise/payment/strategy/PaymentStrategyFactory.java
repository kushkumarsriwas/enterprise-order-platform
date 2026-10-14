package com.enterprise.payment.strategy;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory pattern: picks the strategy for a payment method. Spring injects every PaymentStrategy bean. */
@Component
public class PaymentStrategyFactory {

    private final Map<PaymentMethod, PaymentStrategy> strategies = new EnumMap<>(PaymentMethod.class);

    public PaymentStrategyFactory(List<PaymentStrategy> available) {
        available.forEach(strategy -> strategies.put(strategy.method(), strategy));
    }

    public PaymentStrategy forMethod(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw ApiException.badRequest("Payment method " + method + " is not supported");
        }
        return strategy;
    }
}
