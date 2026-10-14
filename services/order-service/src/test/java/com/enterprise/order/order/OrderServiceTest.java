package com.enterprise.order.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.client.PaymentClient;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.order.OrderDtos.OrderLineRequest;
import com.enterprise.order.order.OrderDtos.PayOrderRequest;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private NotificationPublisher notifications;

    private OrderService service;
    private final AuthenticatedUser customer = new AuthenticatedUser(7L, "kush", "kush@example.com", "CUSTOMER");

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new OrderService(orderRepository, inventoryClient, paymentClient, notifications, tx,
                Duration.ofMinutes(30));
    }

    @Test
    void placingAnOrderUsesServerSidePrices() {
        when(inventoryClient.reserve(anyString(), anyList())).thenReturn(new InventoryClient.Reservation("ref", "AED",
                Instant.now(), List.of(new InventoryClient.Line(1L, "SKU-1", "Laptop", new BigDecimal("4999.00"), 2))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.placeOrder(customer, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 2)),
                "Business Bay, Dubai, UAE"));

        assertThat(response.totalAmount()).isEqualByComparingTo("9998.00");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.orderNumber()).startsWith("ORD-");
    }

    @Test
    void failedSaveReleasesTheReservedStock() {
        when(inventoryClient.reserve(anyString(), anyList())).thenReturn(new InventoryClient.Reservation("ref", "AED",
                Instant.now(), List.of(new InventoryClient.Line(1L, "SKU-1", "Laptop", BigDecimal.TEN, 1))));
        when(orderRepository.save(any(Order.class))).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.placeOrder(customer,
                new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 1)), "Business Bay, Dubai, UAE")))
                .isInstanceOf(IllegalStateException.class);
        verify(inventoryClient).release(anyString());
    }

    @Test
    void failedPaymentKeepsTheOrderPendingAndDoesNotCommitStock() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(new PaymentClient.PaymentResult(5L, "TXN-1", 1L,
                order.getTotalAmount(), "CARD", "FAILED", "**** 0002", "Card declined by the issuing bank."));

        var response = service.pay(customer, 1L, new PayOrderRequest("CARD", "4000000000000002", "Kush", "12/30", "123", null));

        assertThat(response.order().status()).isEqualTo("PENDING");
        assertThat(response.payment().status()).isEqualTo("FAILED");
        assertThat(order.isPaymentInProgress()).isFalse();
        verify(inventoryClient, never()).commit(anyString());
    }

    @Test
    void successfulPaymentConfirmsTheOrder() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(new PaymentClient.PaymentResult(5L, "TXN-2", 1L,
                order.getTotalAmount(), "CARD", "SUCCESS", "**** 1111", null));

        var response = service.pay(customer, 1L, new PayOrderRequest("CARD", "4111111111111111", "Kush", "12/30", "123", null));

        assertThat(response.order().status()).isEqualTo("CONFIRMED");
        assertThat(response.order().paymentState()).isEqualTo("PAID");
        verify(inventoryClient).commit(order.getOrderNumber());
    }

    @Test
    void customersCannotPayForSomeoneElsesOrder() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        AuthenticatedUser stranger = new AuthenticatedUser(99L, "eve", "eve@example.com", "CUSTOMER");

        assertThatThrownBy(() -> service.pay(stranger, 1L, new PayOrderRequest("COD", null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("your own orders");
        verify(paymentClient, never()).charge(any());
    }

    @Test
    void cancellingAPaidOrderRefundsAndReleasesStock() {
        Order order = pendingOrder();
        order.confirmPayment(5L, "CARD", PaymentState.PAID, "kush", "paid");
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var response = service.cancel(customer, 1L, "Changed my mind");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.paymentState()).isEqualTo("REFUNDED");
        verify(paymentClient).refund(eq(1L), eq("Changed my mind"));
        verify(inventoryClient).release(order.getOrderNumber());
    }

    private Order pendingOrder() {
        Order order = new Order("ORD-TEST-1", 7L, "kush", "kush@example.com", "AED", "Business Bay, Dubai, UAE");
        order.addItem(new OrderItem(1L, "SKU-1", "Laptop", new BigDecimal("4999.00"), 1));
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        return order;
    }
}
