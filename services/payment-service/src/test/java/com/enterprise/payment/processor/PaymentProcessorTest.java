package com.enterprise.payment.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentMethod;
import com.enterprise.payment.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentProcessorTest {

    private final PaymentProcessorFactory factory = new PaymentProcessorFactory(List.of(
            new CardPaymentProcessor(), new UpiPaymentProcessor(), new CashOnDeliveryProcessor()));

    private static PaymentCommand card(String number, String expiry, String amount) {
        return new PaymentCommand("ORD-1", new BigDecimal(amount), "AED", number, "Kush Kumar", expiry, "123", null);
    }

    @Test
    void factoryReturnsTheStrategyForEachMethod() {
        assertThat(factory.forMethod(PaymentMethod.CARD)).isInstanceOf(CardPaymentProcessor.class);
        assertThat(factory.forMethod(PaymentMethod.UPI)).isInstanceOf(UpiPaymentProcessor.class);
        assertThat(factory.forMethod(PaymentMethod.COD)).isInstanceOf(CashOnDeliveryProcessor.class);
    }

    @Test
    void validCardIsApprovedAndMasked() {
        ProcessorResult result = factory.forMethod(PaymentMethod.CARD).process(card("4111 1111 1111 1111", "12/39", "100.00"));
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.maskedInstrument()).isEqualTo("VISA **** 1111");
    }

    @Test
    void issuerDeclineIsAFailedPaymentNotAnError() {
        ProcessorResult result = factory.forMethod(PaymentMethod.CARD).process(card("4000000000000002", "12/39", "100.00"));
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).contains("declined");
    }

    @Test
    void invalidCardDetailsAreRejectedUpFront() {
        CardPaymentProcessor processor = new CardPaymentProcessor();
        assertThatThrownBy(() -> processor.process(card("4111111111111112", "12/39", "1.00")))
                .isInstanceOf(ApiException.class).hasMessageContaining("not valid");
        assertThatThrownBy(() -> processor.process(card("4111111111111111", "01/20", "1.00")))
                .isInstanceOf(ApiException.class).hasMessageContaining("expired");
    }

    @Test
    void luhnChecksum() {
        assertThat(CardPaymentProcessor.passesLuhn("4111111111111111")).isTrue();
        assertThat(CardPaymentProcessor.passesLuhn("5555555555554444")).isTrue();
        assertThat(CardPaymentProcessor.passesLuhn("1234567812345678")).isFalse();
    }

    @Test
    void cashOnDeliveryIsPendingAndLimited() {
        PaymentProcessor cod = factory.forMethod(PaymentMethod.COD);
        assertThat(cod.process(new PaymentCommand("ORD-1", new BigDecimal("4999.00"), "AED", null, null, null, null, null))
                .status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(cod.process(new PaymentCommand("ORD-1", new BigDecimal("5000.01"), "AED", null, null, null, null, null))
                .status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void upiValidatesTheAddress() {
        PaymentProcessor upi = factory.forMethod(PaymentMethod.UPI);
        assertThat(upi.process(new PaymentCommand("ORD-1", BigDecimal.TEN, "AED", null, null, null, null, "kush@okbank"))
                .status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThatThrownBy(() -> upi.process(new PaymentCommand("ORD-1", BigDecimal.TEN, "AED", null, null, null, null, "no-at-sign")))
                .isInstanceOf(ApiException.class);
    }
}
