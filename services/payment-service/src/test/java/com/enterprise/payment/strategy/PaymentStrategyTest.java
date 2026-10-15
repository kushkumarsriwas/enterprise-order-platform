package com.enterprise.payment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.payment.PaymentStatus;
import com.enterprise.payment.processor.FutureRealPaymentProcessor;
import com.enterprise.payment.processor.MockPaymentProcessor;
import com.enterprise.payment.processor.PaymentProcessor;
import com.enterprise.payment.processor.PaymentProcessorFactory;
import com.enterprise.payment.processor.PaymentResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Strategy (per payment method) + Factory + PaymentProcessor (mock provider), without Spring. */
class PaymentStrategyTest {

    private final PaymentStrategyFactory strategies = new PaymentStrategyFactory(List.of(new CardPayment(),
            new UpiPayment(), new CashOnDelivery()));
    private final PaymentProcessor mock = new MockPaymentProcessor();

    private static PaymentDetails card(String number, String expiry) {
        return new PaymentDetails(number, "Kush Kumar", expiry, "123", null);
    }

    private PaymentResult pay(PaymentMethod method, PaymentDetails details, String amount) {
        PaymentStrategy strategy = strategies.forMethod(method);
        strategy.validate(details);
        return strategy.pay("ORD-1", new BigDecimal(amount), "AED", details, mock);
    }

    @Test
    void factoryReturnsTheStrategyForEachMethod() {
        assertThat(strategies.forMethod(PaymentMethod.CARD)).isInstanceOf(CardPayment.class);
        assertThat(strategies.forMethod(PaymentMethod.UPI)).isInstanceOf(UpiPayment.class);
        assertThat(strategies.forMethod(PaymentMethod.COD)).isInstanceOf(CashOnDelivery.class);
    }

    @Test
    void validCardIsApprovedAndOnlyTheMaskedNumberIsKept() {
        PaymentResult result = pay(PaymentMethod.CARD, card("4111 1111 1111 1111", "12/39"), "100.00");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.maskedInstrument()).isEqualTo("VISA **** 1111");
        assertThat(result.providerReference()).startsWith("MOCK-");
    }

    @Test
    void issuerDeclineIsAFailedPaymentNotAnError() {
        PaymentResult declined = pay(PaymentMethod.CARD, card("4000000000000002", "12/39"), "100.00");
        assertThat(declined.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.failureReason()).contains("declined");
        PaymentResult noFunds = pay(PaymentMethod.CARD, card("4000000000009995", "12/39"), "100.00");
        assertThat(noFunds.failureReason()).contains("Insufficient funds");
    }

    @Test
    void invalidCardDetailsAreRejectedBeforeAnythingIsCharged() {
        CardPayment strategy = new CardPayment();
        assertThatThrownBy(() -> strategy.validate(card("4111111111111112", "12/39")))
                .isInstanceOf(ApiException.class).hasMessageContaining("not valid");
        assertThatThrownBy(() -> strategy.validate(card("4111111111111111", "01/20")))
                .isInstanceOf(ApiException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> strategy.validate(new PaymentDetails("4111111111111111", "Kush", "12/39", "1", null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("CVV");
    }

    @Test
    void luhnChecksumAndBrands() {
        assertThat(CardPayment.passesLuhn("4111111111111111")).isTrue();
        assertThat(CardPayment.passesLuhn("5555555555554444")).isTrue();
        assertThat(CardPayment.passesLuhn("1234567812345678")).isFalse();
        assertThat(CardPayment.brand("5555555555554444")).isEqualTo("MASTERCARD");
        assertThat(CardPayment.brand("378282246310005")).isEqualTo("AMEX");
    }

    @Test
    void cashOnDeliveryIsPendingAndLimitedToFiveThousand() {
        PaymentDetails none = new PaymentDetails(null, null, null, null, null);
        assertThat(pay(PaymentMethod.COD, none, "4999.00").status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pay(PaymentMethod.COD, none, "5000.01").status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void upiValidatesTheAddressAndTheBankCanDecline() {
        assertThat(pay(PaymentMethod.UPI, new PaymentDetails(null, null, null, null, "Kush@OKBank"), "10.00").status())
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pay(PaymentMethod.UPI, new PaymentDetails(null, null, null, null, "fail.test@okbank"), "10.00")
                .status()).isEqualTo(PaymentStatus.FAILED);
        assertThatThrownBy(() -> pay(PaymentMethod.UPI, new PaymentDetails(null, null, null, null, "no-at-sign"), "10"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void theProcessorFactoryPicksTheConfiguredProvider() {
        List<PaymentProcessor> available = List.of(new MockPaymentProcessor(), new FutureRealPaymentProcessor());
        assertThat(new PaymentProcessorFactory(available, "mock").processor()).isInstanceOf(MockPaymentProcessor.class);
        PaymentProcessor real = new PaymentProcessorFactory(available, "real").processor();
        assertThatThrownBy(() -> real.process(null)).isInstanceOf(ApiException.class)
                .hasMessageContaining("not enabled");
        assertThatThrownBy(() -> new PaymentProcessorFactory(available, "paypal"))
                .isInstanceOf(IllegalStateException.class);
    }
}
