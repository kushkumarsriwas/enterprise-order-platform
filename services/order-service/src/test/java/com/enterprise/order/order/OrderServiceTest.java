package com.enterprise.order.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.enterprise.order.order.OrderDtos.AddressRequest;
import com.enterprise.order.order.OrderDtos.OrderLineRequest;
import com.enterprise.order.order.OrderDtos.PaymentOutcomeRequest;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.order.OrderDtos.StatusUpdateRequest;
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

/** Unit tests of the order saga with Mockito: the other services and the database are mocked. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final AddressRequest ADDRESS = new AddressRequest("Kush Kumar", "+971 50 123 4567",
            "Marina Walk, Tower 3, Apt 1204", null, "Dubai", "Dubai", null);

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
    private final AuthenticatedUser seller = new AuthenticatedUser(50L, "gulf", "s@gulf.ae", "SELLER", "Gulf Gadgets");

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new OrderService(orderRepository, inventoryClient, paymentClient, notifications, tx,
                Duration.ofMinutes(30), Duration.ofMinutes(2));
        Mockito.lenient().when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static InventoryClient.Reservation reservation(String ref, long productId, long sellerId, String store,
                                                           String price, int qty) {
        return new InventoryClient.Reservation(ref, "AED", Instant.now(), List.of(new InventoryClient.Line(productId,
                sellerId, store, "SKU-" + productId, "Product " + productId, "Electronics", new BigDecimal(price),
                new BigDecimal("10.00"), qty)));
    }

    // ------------------------------------------------------------------ checkout

    @Test
    void checkoutIsSplitIntoOneOrderPerSellerWithServerSidePrices() {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(
                new ProductInfo(1L, 50L, "Gulf Gadgets", "Phone", new BigDecimal("2500"), true, 9),
                new ProductInfo(2L, 60L, "Desert Deals", "Lamp", new BigDecimal("120"), true, 9)));
        when(inventoryClient.reserve(anyString(), anyList()))
                .thenAnswer(inv -> reservation(inv.getArgument(0), 1L, 50L, "Gulf Gadgets", "2500.00", 2))
                .thenAnswer(inv -> reservation(inv.getArgument(0), 2L, 60L, "Desert Deals", "120.00", 1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var checkout = service.placeOrder(customer, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 2),
                new OrderLineRequest(2L, 1)), ADDRESS));

        assertThat(checkout.orders()).hasSize(2);
        assertThat(checkout.orders()).extracting("sellerName").containsExactly("Gulf Gadgets", "Desert Deals");
        assertThat(checkout.grandTotal()).isEqualByComparingTo("5120.00");
        assertThat(checkout.orders().get(0).recipientName()).isEqualTo("Kush Kumar");
        assertThat(checkout.orders().get(0).shippingAddress())
                .isEqualTo("Kush Kumar, Marina Walk, Tower 3, Apt 1204, Dubai, United Arab Emirates (+971 50 123 4567)");
        assertThat(checkout.orders().get(0).items().get(0).categoryName()).isEqualTo("Electronics");
        // VAT is included in the price: 5000 x 5 / 105
        assertThat(checkout.orders().get(0).vatAmount()).isEqualByComparingTo("238.10");
        verify(inventoryClient, times(2)).reserve(anyString(), anyList());
    }

    @Test
    void ifOneSellerIsOutOfStockTheOtherReservationsAreReleased() {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(
                new ProductInfo(1L, 50L, "Gulf Gadgets", "Phone", new BigDecimal("2500"), true, 9),
                new ProductInfo(2L, 60L, "Desert Deals", "Lamp", new BigDecimal("120"), true, 0)));
        when(inventoryClient.reserve(anyString(), anyList()))
                .thenAnswer(inv -> reservation(inv.getArgument(0), 1L, 50L, "Gulf Gadgets", "2500.00", 1))
                .thenThrow(ApiException.conflict("Insufficient stock for 'Lamp'"));

        assertThatThrownBy(() -> service.placeOrder(customer, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 1),
                new OrderLineRequest(2L, 1)), ADDRESS)))
                .hasMessageContaining("Insufficient stock");
        verify(inventoryClient).release(anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unavailableProductsStopTheCheckoutBeforeAnythingIsReserved() {
        when(inventoryClient.lookup(anyList())).thenReturn(List.of(
                new ProductInfo(1L, 50L, "Gulf Gadgets", "Phone", new BigDecimal("2500"), false, 0)));

        assertThatThrownBy(() -> service.placeOrder(customer,
                new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 1)), ADDRESS)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no longer available");
        verify(inventoryClient, never()).reserve(anyString(), anyList());
    }

    @Test
    void sellersCannotPlaceOrders() {
        assertThatThrownBy(() -> service.placeOrder(seller, new PlaceOrderRequest(List.of(new OrderLineRequest(1L, 1)),
                ADDRESS)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only customer accounts");
    }

    // ------------------------------------------------------------------ payment

    @Test
    void paymentIntentTakesTheAmountFromTheOrderAndAllowsOneAttemptAtATime() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var intent = service.startPayment(customer, 1L);

        assertThat(intent.amount()).isEqualByComparingTo("4999.00");
        assertThat(intent.sellerId()).isEqualTo(50L);
        assertThat(order.isPaymentInProgress()).isTrue();
        assertThatThrownBy(() -> service.startPayment(customer, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already being processed");
    }

    @Test
    void customersCannotPayForSomeoneElsesOrder() {
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(pendingOrder()));
        AuthenticatedUser stranger = new AuthenticatedUser(99L, "eve", "eve@example.com", "CUSTOMER", null);

        assertThatThrownBy(() -> service.startPayment(stranger, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void ordersCannotBePaidAfterThePaymentWindow() {
        Order order = pendingOrder();
        ReflectionTestUtils.setField(order, "createdAt", Instant.now().minus(Duration.ofMinutes(31)));
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.startPayment(customer, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("payment window");
    }

    @Test
    void failedPaymentKeepsTheOrderPendingAndDoesNotCommitStock() {
        Order order = pendingOrder();
        order.startPaymentAttempt();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var outcome = service.applyPaymentOutcome(customer, 1L, new PaymentOutcomeRequest(5L, "TXN-1", "CARD",
                "FAILED", "VISA **** 0002", "Card declined by the issuing bank."));

        assertThat(outcome.confirmed()).isFalse();
        assertThat(outcome.refundRequired()).isFalse();
        assertThat(outcome.order().status()).isEqualTo("PENDING");
        assertThat(order.isPaymentInProgress()).isFalse();
        verify(inventoryClient, never()).commit(anyString());
        verify(notifications).toUser(any(), eq(7L), eq("PAYMENT_FAILED"), anyString(), anyString());
    }

    @Test
    void successfulPaymentCommitsTheStockConfirmsTheOrderAndAlertsTheSeller() {
        Order order = pendingOrder();
        order.startPaymentAttempt();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var outcome = service.applyPaymentOutcome(customer, 1L, new PaymentOutcomeRequest(5L, "TXN-2", "CARD",
                "SUCCESS", "VISA **** 1111", null));

        assertThat(outcome.confirmed()).isTrue();
        assertThat(outcome.order().status()).isEqualTo("CONFIRMED");
        assertThat(outcome.order().paymentState()).isEqualTo("PAID");
        assertThat(outcome.order().invoiceAvailable()).isTrue();
        verify(inventoryClient).commit(order.getOrderNumber());
        verify(notifications).toUser(any(), eq(50L), eq("NEW_ORDER"), anyString(), anyString());
    }

    @Test
    void cashOnDeliveryIsConfirmedAndCollectedLater() {
        Order order = pendingOrder();
        order.startPaymentAttempt();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var outcome = service.applyPaymentOutcome(customer, 1L, new PaymentOutcomeRequest(6L, "TXN-3", "COD",
                "PENDING", "Cash on delivery", null));

        assertThat(outcome.order().paymentState()).isEqualTo("PAY_ON_DELIVERY");
        assertThat(outcome.order().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void reportingTheSamePaymentTwiceIsHarmless() {
        Order order = pendingOrder();
        order.startPaymentAttempt();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        PaymentOutcomeRequest success = new PaymentOutcomeRequest(5L, "TXN-2", "UPI", "SUCCESS", "kush@okbank", null);

        service.applyPaymentOutcome(customer, 1L, success);
        var again = service.applyPaymentOutcome(AuthenticatedUser.SERVICE, 1L, success);

        assertThat(again.confirmed()).isTrue();
        assertThat(again.refundRequired()).isFalse();
        verify(inventoryClient, times(1)).commit(order.getOrderNumber());
    }

    @Test
    void ifTheStockCanNoLongerBeCommittedTheOrderIsCancelledAndTheMoneyRefunded() {
        Order order = pendingOrder();
        order.startPaymentAttempt();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        doThrow(ApiException.conflict("Reservation expired")).when(inventoryClient).commit(order.getOrderNumber());

        var outcome = service.applyPaymentOutcome(customer, 1L, new PaymentOutcomeRequest(5L, "TXN-4", "CARD",
                "SUCCESS", "VISA **** 1111", null));

        assertThat(outcome.confirmed()).isFalse();
        assertThat(outcome.refundRequired()).isTrue();
        assertThat(outcome.order().status()).isEqualTo("CANCELLED");
        assertThat(outcome.order().paymentState()).isEqualTo("REFUNDED");
        verify(inventoryClient).release(order.getOrderNumber());
    }

    @Test
    void aPaymentForAnExpiredOrderMustBeRefunded() {
        Order order = pendingOrder();
        order.cancel("system", "Payment not completed within 30 minutes", PaymentState.UNPAID);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var outcome = service.applyPaymentOutcome(customer, 1L, new PaymentOutcomeRequest(5L, "TXN-5", "CARD",
                "SUCCESS", "VISA **** 1111", null));

        assertThat(outcome.refundRequired()).isTrue();
        verify(inventoryClient, never()).commit(anyString());
    }

    // ------------------------------------------------------------------ cancellation and fulfilment

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
    void ordersCannotBeCancelledWhileAPaymentIsRunning() {
        Order order = pendingOrder();
        order.startPaymentAttempt();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(customer, 1L, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("payment is being processed");
        verify(paymentClient, never()).refund(anyLong(), anyString());
    }

    @Test
    void sellerShipsAndDeliversCashOnDeliveryOrders() {
        Order order = pendingOrder();
        order.confirmPayment(5L, "COD", PaymentState.PAY_ON_DELIVERY, "kush", "cod");
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        var shipped = service.updateStatus(seller, 1L, new StatusUpdateRequest(OrderStatus.SHIPPED, "Aramex", "AWB123",
                null));
        assertThat(shipped.status()).isEqualTo("SHIPPED");
        assertThat(shipped.trackingNumber()).isEqualTo("AWB123");

        var delivered = service.updateStatus(seller, 1L, new StatusUpdateRequest(OrderStatus.DELIVERED, null, null,
                null));
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.paymentState()).isEqualTo("PAID");
        verify(paymentClient).capture(1L);
    }

    @Test
    void shippingNeedsACarrierAndATrackingNumber() {
        assertThatThrownBy(() -> service.updateStatus(seller, 1L, new StatusUpdateRequest(OrderStatus.SHIPPED, "Aramex",
                " ", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tracking number");
    }

    @Test
    void sellersCannotTouchOtherSellersOrders() {
        Order order = pendingOrder();
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        AuthenticatedUser otherSeller = new AuthenticatedUser(60L, "desert", "d@x.ae", "SELLER", "Desert Deals");

        assertThatThrownBy(() -> service.ship(otherSeller, 1L, "Aramex", "AWB1", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
        verify(paymentClient, never()).capture(anyLong());
    }

    private Order pendingOrder() {
        Order order = new Order("ORD-TEST-1", 7L, "kush", "kush@example.com", "+971501234567", 50L, "Gulf Gadgets",
                "AED", "Kush Kumar, Business Bay, Dubai, United Arab Emirates (+971501234567)");
        order.deliverTo("Kush Kumar", "Dubai", "Dubai");
        order.addItem(new OrderItem(1L, "SKU-1", "Laptop", "Computers & Office", new BigDecimal("4999.00"),
                new BigDecimal("4000.00"), 1));
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        return order;
    }
}
