package com.enterprise.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.payment.client.OrderClient.OrderSummary;
import com.enterprise.payment.client.OrderClient.OutcomeRequest;
import com.enterprise.payment.client.OrderClient.PaymentIntent;
import com.enterprise.payment.client.OrderClient.PaymentOutcome;
import com.enterprise.payment.client.OrderClient;
import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentDtos.PayOrderRequest;
import com.enterprise.payment.processor.MockPaymentProcessor;
import com.enterprise.payment.processor.PaymentProcessorFactory;
import com.enterprise.payment.security.AuthenticatedUser;
import com.enterprise.payment.strategy.CardPayment;
import com.enterprise.payment.strategy.CashOnDelivery;
import com.enterprise.payment.strategy.PaymentStrategyFactory;
import com.enterprise.payment.strategy.UpiPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit tests of the payment workflow with Mockito (order service and database mocked). */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String VISA = "4111111111111111";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderClient orderClient;

    private PaymentService service;
    private final AtomicReference<Payment> saved = new AtomicReference<>();
    private final AuthenticatedUser customer = new AuthenticatedUser(7L, "kush", "kush@example.com", "CUSTOMER", null);

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository,
                new PaymentStrategyFactory(List.of(new CardPayment(), new UpiPayment(), new CashOnDelivery())),
                new PaymentProcessorFactory(List.of(new MockPaymentProcessor()), "mock"), orderClient,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), new BigDecimal("0.05"));
    }

    private void orderAwaitsPayment(String amount) {
        when(orderClient.paymentIntent(1L)).thenReturn(new PaymentIntent(1L, "ORD-1001", 7L, "kush", "kush@example.com",
                50L, "Gulf Gadgets", new BigDecimal(amount), "AED"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 11L);
            saved.set(payment);
            return payment;
        });
        org.mockito.Mockito.lenient().when(paymentRepository.findById(11L))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
    }

    private static PayOrderRequest card(String number) {
        return new PayOrderRequest(1L, PaymentMethod.CARD, number, "Kush Kumar", "12/39", "123", null);
    }

    @Test
    void successfulCardPaymentTakesTheAmountFromTheOrderKeepsTheFeeAndConfirmsTheOrder() {
        orderAwaitsPayment("1200.00");
        when(orderClient.reportOutcome(eq(1L), any())).thenReturn(new PaymentOutcome(true, false, "Order confirmed",
                new OrderSummary(1L, "ORD-1001", "CONFIRMED", "PAID")));

        var response = service.pay(customer, card(VISA));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.orderConfirmed()).isTrue();
        assertThat(response.orderStatus()).isEqualTo("CONFIRMED");
        assertThat(saved.get().getAmount()).isEqualByComparingTo("1200.00");
        assertThat(saved.get().getPlatformFee()).isEqualByComparingTo("60.00");
        assertThat(saved.get().getSellerNet()).isEqualByComparingTo("1140.00");
        assertThat(saved.get().isOrderUpdatePending()).isFalse();
        ArgumentCaptor<OutcomeRequest> outcome = ArgumentCaptor.forClass(OutcomeRequest.class);
        verify(orderClient).reportOutcome(eq(1L), outcome.capture());
        assertThat(outcome.getValue().status()).isEqualTo("SUCCESS");
        assertThat(outcome.getValue().paymentId()).isEqualTo(11L);
    }

    @Test
    void declinedCardIsRecordedAsFailedAndTheOrderIsToldSoItCanBeRetried() {
        orderAwaitsPayment("1200.00");
        when(orderClient.reportOutcome(eq(1L), any())).thenReturn(new PaymentOutcome(false, false,
                "Card declined by the issuing bank.", new OrderSummary(1L, "ORD-1001", "PENDING", "UNPAID")));

        var response = service.pay(customer, card("4000000000000002"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.orderConfirmed()).isFalse();
        assertThat(response.message()).contains("declined");
        assertThat(saved.get().getPlatformFee()).isEqualByComparingTo("0");
    }

    @Test
    void paymentForAnOrderThatCanNoLongerBeConfirmedIsRefundedAutomatically() {
        orderAwaitsPayment("1200.00");
        when(orderClient.reportOutcome(eq(1L), any())).thenReturn(new PaymentOutcome(false, true,
                "Order ORD-1001 could not be confirmed because the reserved stock is no longer available.",
                new OrderSummary(1L, "ORD-1001", "CANCELLED", "REFUNDED")));

        var response = service.pay(customer, card(VISA));

        assertThat(response.status()).isEqualTo("REFUNDED");
        assertThat(response.orderStatus()).isEqualTo("CANCELLED");
        assertThat(saved.get().getRefundReason()).contains("could not be confirmed");
    }

    @Test
    void ifTheOrderServiceIsDownThePaymentIsKeptAndRetriedLater() {
        orderAwaitsPayment("300.00");
        when(orderClient.reportOutcome(eq(1L), any())).thenThrow(ApiException.serviceUnavailable("Order service down"));

        var response = service.pay(customer, new PayOrderRequest(1L, PaymentMethod.UPI, null, null, null, null,
                "kush@okbank"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.orderStatus()).isEqualTo("PENDING");
        assertThat(saved.get().isOrderUpdatePending()).isTrue();
        assertThat(saved.get().getOrderUpdateAttempts()).isEqualTo(1);
    }

    @Test
    void invalidDetailsAreRejectedBeforeTheOrderIsLocked() {
        assertThatThrownBy(() -> service.pay(customer, card("1234")))
                .isInstanceOf(ApiException.class).hasMessageContaining("card number");
        verify(orderClient, never()).paymentIntent(anyLong());
    }

    @Test
    void anOrderAlreadyPaidIsNeverChargedTwice() {
        when(orderClient.paymentIntent(1L)).thenReturn(new PaymentIntent(1L, "ORD-1001", 7L, "kush", null, 50L,
                "Gulf Gadgets", new BigDecimal("50.00"), "AED"));
        Payment existing = new Payment("TXN-1", 1L, "ORD-1001", 7L, "kush", 50L, "Gulf Gadgets",
                new BigDecimal("50.00"), "AED", PaymentMethod.CARD, PaymentStatus.SUCCESS, "mock", "MOCK-1",
                "VISA **** 1111", null, new BigDecimal("2.50"));
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(paymentRepository.findFirstByOrderIdAndStatusIn(eq(1L), any())).thenReturn(Optional.of(existing));
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(orderClient.reportOutcome(eq(1L), any())).thenReturn(new PaymentOutcome(true, false, "Order confirmed",
                new OrderSummary(1L, "ORD-1001", "CONFIRMED", "PAID")));

        var response = service.pay(customer, card(VISA));

        assertThat(response.transactionReference()).isEqualTo("TXN-1");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void onlyCustomersPay() {
        AuthenticatedUser seller = new AuthenticatedUser(50L, "gulf", null, "SELLER", "Gulf Gadgets");
        assertThatThrownBy(() -> service.pay(seller, card(VISA))).isInstanceOf(ApiException.class)
                .hasMessageContaining("Only customer accounts");
    }
}
