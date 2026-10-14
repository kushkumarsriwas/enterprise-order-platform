package com.enterprise.order.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.InventoryClient.ProductInfo;
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
import org.mockito.Mockito;
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
    private final AuthenticatedUser customer = new AuthenticatedUser(7L, "kush", "kush@example.com", "CUSTOMER", null);
    private final AuthenticatedUser merchant = new AuthenticatedUser(50L, "gulf", "s@gulf.ae", "MERCHANT", "Gulf Gadgets");

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new OrderService(orderRepository, inventoryClient, paymentClient, notifications, tx,
                Duration.ofMinutes(30));
        Mockito.lenient().when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static InventoryClient.Reservation reservation(String ref, long productId, long merchantId, String store,
                                                           String price, int qty) {
        return new InventoryClient.Reservation(ref, "AED", Instant.now(), List.of(new InventoryClient.Line(productId,
                merchantId, store, "SKU-" + productId, "Product " + productId, new BigDecimal(price),
                new BigDecimal("10.00"), qty)));
    }

    @Test
    void checkoutIsSplitIntoOneOrderPerMerchantWithServerSidePrices() {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(
                new ProductInfo(1L, 50L, "Gulf Gadgets", "Phone", new BigDecimal("2500"), true, 9),
                new ProductInfo(2L, 60L, "Desert Deals", "Lamp", new BigDecimal("120"), true, 9)));
        when(inventoryClient.reserve(anyString(), anyList()))
                .thenAnswer(inv -> reservation(inv.getArgument(0), 1L, 50L, "Gulf Gadgets", "2500.00", 2))
                .thenAnswer(inv -> reservation(inv.getArgument(0), 2L, 60L, "Desert Deals", "120.00", 1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var checkout = service.placeOrder(customer, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 2),
                new OrderLineRequest(2L, 1)), "Business Bay, Dubai, UAE", "+971501234567"));

        assertThat(checkout.orders()).hasSize(2);
        assertThat(checkout.orders()).extracting("merchantName").containsExactly("Gulf Gadgets", "Desert Deals");
        assertThat(checkout.grandTotal()).isEqualByComparingTo("5120.00");
        verify(inventoryClient, times(2)).reserve(anyString(), anyList());
    }

    @Test
    void ifOneMerchantIsOutOfStockTheOtherReservationsAreReleased() {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(
                new ProductInfo(1L, 50L, "Gulf Gadgets", "Phone", new BigDecimal("2500"), true, 9),
                new ProductInfo(2L, 60L, "Desert Deals", "Lamp", new BigDecimal("120"), true, 0)));
        when(inventoryClient.reserve(anyString(), anyList()))
                .thenAnswer(inv -> reservation(inv.getArgument(0), 1L, 50L, "Gulf Gadgets", "2500.00", 1))
                .thenThrow(ApiException.conflict("Insufficient stock for 'Lamp'"));

        assertThatThrownBy(() -> service.placeOrder(customer, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 1),
                new OrderLineRequest(2L, 1)), "Business Bay, Dubai, UAE", null)))
                .hasMessageContaining("Insufficient stock");
        verify(inventoryClient).release(anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void merchantsCannotPlaceOrders() {
        assertThatThrownBy(() -> service.placeOrder(merchant, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 1)),
                "Business Bay, Dubai, UAE", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only customer accounts");
    }

    @Test
    void failedPaymentKeepsTheOrderPendingAndDoesNotCommitStock() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(new PaymentClient.PaymentResult(5L, "TXN-1", 1L,
                order.getTotalAmount(), "CARD", "FAILED", "VISA **** 0002", "Card declined by the issuing bank."));

        var response = service.pay(customer, 1L, new PayOrderRequest("CARD", "4000000000000002", "Kush", "12/30", "123", null));

        assertThat(response.order().status()).isEqualTo("PENDING");
        assertThat(response.payment().status()).isEqualTo("FAILED");
        assertThat(order.isPaymentInProgress()).isFalse();
        verify(inventoryClient, never()).commit(anyString());
    }

    @Test
    void successfulPaymentConfirmsTheOrderAndAlertsTheMerchant() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(new PaymentClient.PaymentResult(5L, "TXN-2", 1L,
                order.getTotalAmount(), "CARD", "SUCCESS", "VISA **** 1111", null));

        var response = service.pay(customer, 1L, new PayOrderRequest("CARD", "4111111111111111", "Kush", "12/30", "123", null));

        assertThat(response.order().status()).isEqualTo("CONFIRMED");
        assertThat(response.order().paymentState()).isEqualTo("PAID");
        verify(inventoryClient).commit(order.getOrderNumber());
        verify(notifications).toUser(any(), eq(50L), eq("NEW_ORDER"), anyString(), anyString());
    }

    @Test
    void customersCannotPayForSomeoneElsesOrder() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        AuthenticatedUser stranger = new AuthenticatedUser(99L, "eve", "eve@example.com", "CUSTOMER", null);

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

    @Test
    void merchantShipsAndDeliversCashOnDeliveryOrders() {
        Order order = pendingOrder();
        order.confirmPayment(5L, "COD", PaymentState.PAY_ON_DELIVERY, "kush", "cod");
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var shipped = service.ship(merchant, 1L, "Aramex", "AWB123", null);
        assertThat(shipped.status()).isEqualTo("SHIPPED");
        assertThat(shipped.trackingNumber()).isEqualTo("AWB123");

        var delivered = service.deliver(merchant, 1L);
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.paymentState()).isEqualTo("PAID");
        verify(paymentClient).capture(1L);
    }

    @Test
    void merchantsCannotTouchOtherMerchantsOrders() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        AuthenticatedUser otherMerchant = new AuthenticatedUser(60L, "desert", "d@x.ae", "MERCHANT", "Desert Deals");

        assertThatThrownBy(() -> service.ship(otherMerchant, 1L, "Aramex", "AWB1", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
        verify(paymentClient, never()).capture(anyLong());
    }

    private Order pendingOrder() {
        Order order = new Order("ORD-TEST-1", 7L, "kush", "kush@example.com", null, 50L, "Gulf Gadgets", "AED",
                "Business Bay, Dubai, UAE");
        order.addItem(new OrderItem(1L, "SKU-1", "Laptop", new BigDecimal("4999.00"), new BigDecimal("4000.00"), 1));
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        return order;
    }
}
