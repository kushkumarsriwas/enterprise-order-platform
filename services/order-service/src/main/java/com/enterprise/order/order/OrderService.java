package com.enterprise.order.order;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.InventoryClient.ProductInfo;
import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.client.PaymentClient;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.order.OrderDtos.AddressRequest;
import com.enterprise.order.order.OrderDtos.CheckoutResponse;
import com.enterprise.order.order.OrderDtos.OrderLineRequest;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.PaymentIntent;
import com.enterprise.order.order.OrderDtos.PaymentOutcomeRequest;
import com.enterprise.order.order.OrderDtos.PaymentOutcomeResponse;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.order.OrderDtos.StatusUpdateRequest;
import com.enterprise.order.security.AuthenticatedUser;
import com.enterprise.order.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saga coordinator of the marketplace order workflow:
 * <pre>
 * checkout: split the cart per seller -> reserve stock per seller -> save PENDING orders | compensation: release stock
 * payment:  (payment service) intent -> charge -> outcome -> commit stock -> CONFIRMED   | compensation: cancel + refund
 * fulfil:   the seller ships (carrier + tracking) -> delivers (cash collected for COD)
 * cancel:   refund (payment service) -> release stock -> CANCELLED
 * </pre>
 * Remote calls are made outside database transactions, so no connection is held while waiting on the network.
 * Every state change goes through {@link Order}, whose {@code @Version} column rejects concurrent updates.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    static final String CURRENCY = "AED";

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final NotificationPublisher notifications;
    private final TransactionTemplate tx;
    private final Duration paymentWindow;
    private final Duration paymentAttemptTimeout;

    public OrderService(OrderRepository orderRepository, InventoryClient inventoryClient, PaymentClient paymentClient,
                        NotificationPublisher notifications, TransactionTemplate tx,
                        @Value("${app.orders.payment-window:30m}") Duration paymentWindow,
                        @Value("${app.orders.payment-attempt-timeout:2m}") Duration paymentAttemptTimeout) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.notifications = notifications;
        this.tx = tx;
        this.paymentWindow = paymentWindow;
        this.paymentAttemptTimeout = paymentAttemptTimeout;
    }

    public Duration paymentWindow() {
        return paymentWindow;
    }

    // ================================================================== checkout

    public CheckoutResponse placeOrder(AuthenticatedUser customer, PlaceOrderRequest request) {
        if (!customer.isCustomer()) {
            throw ApiException.forbidden("Only customer accounts can place orders");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : request.items()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        // 1) Which seller sells each product? A marketplace cart becomes one order per seller.
        Map<Long, ProductInfo> products = inventoryClient.lookup(new ArrayList<>(quantities.keySet())).stream()
                .collect(Collectors.toMap(ProductInfo::productId, Function.identity()));
        Map<Long, List<InventoryClient.Item>> bySeller = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            ProductInfo product = products.get(entry.getKey());
            if (product == null || !product.purchasable()) {
                throw ApiException.conflict("Product " + (product == null ? entry.getKey() : "'" + product.name() + "'")
                        + " is no longer available. Please remove it from your cart.");
            }
            bySeller.computeIfAbsent(product.sellerId(), id -> new ArrayList<>())
                    .add(new InventoryClient.Item(entry.getKey(), entry.getValue()));
        }

        // 2) Reserve stock for every seller's order; if one fails, release the ones already reserved.
        List<InventoryClient.Reservation> reservations = new ArrayList<>();
        try {
            for (List<InventoryClient.Item> items : bySeller.values()) {
                String orderNumber = OrderNumberGenerator.next();
                log.info("Creating order {} for {} ({} product(s))", orderNumber, customer.username(), items.size());
                reservations.add(inventoryClient.reserve(orderNumber, items));
            }
        } catch (RuntimeException ex) {
            log.info("Checkout of {} stopped: {}", customer.username(), ex.getMessage());
            reservations.forEach(reservation -> safeRelease(reservation.orderRef()));
            throw ex;
        }

        // 3) Save all orders in ONE local transaction; if that fails, compensate by releasing every reservation.
        AddressRequest address = request.address();
        List<OrderResponse> orders;
        try {
            orders = tx.execute(status -> reservations.stream().map(reservation -> {
                InventoryClient.Line first = reservation.lines().get(0);
                Order order = new Order(reservation.orderRef(), customer.id(), customer.username(), customer.email(),
                        address.phone().trim(), first.sellerId(), first.sellerName(), CURRENCY, address.formatted());
                order.deliverTo(address.recipient().trim(), address.city().trim(), blankToNull(address.region()));
                reservation.lines().forEach(line -> order.addItem(new OrderItem(line.productId(), line.sku(),
                        line.name(), line.categoryName(), line.unitPrice(), line.unitCost(), line.quantity())));
                return OrderMapper.toResponse(orderRepository.save(order), paymentWindow);
            }).toList());
        } catch (RuntimeException ex) {
            log.error("Saving the checkout of {} failed - releasing the reserved stock", customer.username(), ex);
            reservations.forEach(reservation -> safeRelease(reservation.orderRef()));
            throw ex;
        }
        BigDecimal grandTotal = orders.stream().map(OrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        notifications.toUserWithEmail(CurrentUser.token(), customer.id(), customer.email(), "ORDER_PLACED",
                orders.size() == 1 ? "Order " + orders.get(0).orderNumber() + " placed" : orders.size() + " orders placed",
                "Total " + CURRENCY + " " + grandTotal + ". Please complete the payment within "
                        + paymentWindow.toMinutes() + " minutes - your items are reserved until then.");
        log.info("Checkout by {}: {} order(s), total {} {}", customer.username(), orders.size(), grandTotal, CURRENCY);
        return new CheckoutResponse(orders, grandTotal, CURRENCY);
    }

    // ================================================================== payment (called by the payment service)

    /**
     * Step 1 of a payment: checks the order can be paid by this customer and marks a payment attempt as running.
     * The {@code @Version} column turns a concurrent second attempt (double click, two tabs) into a 409.
     */
    public PaymentIntent startPayment(AuthenticatedUser customer, Long orderId) {
        try {
            return tx.execute(status -> {
                Order order = load(orderId);
                if (customer.isService() || !order.isOwnedBy(customer.id())) {
                    throw ApiException.notFound("Order " + orderId + " not found");
                }
                if (order.getStatus() != OrderStatus.PENDING) {
                    throw ApiException.conflict("Order " + order.getOrderNumber() + " is " + order.getStatus()
                            + " and cannot be paid");
                }
                if (isPaymentWindowOver(order)) {
                    throw ApiException.conflict("The payment window for order " + order.getOrderNumber()
                            + " has expired. Please place a new order.");
                }
                if (order.hasActivePaymentAttempt(paymentAttemptTimeout)) {
                    throw ApiException.conflict("A payment for order " + order.getOrderNumber()
                            + " is already being processed");
                }
                order.startPaymentAttempt();
                orderRepository.saveAndFlush(order);
                return new PaymentIntent(order.getId(), order.getOrderNumber(), order.getUserId(),
                        order.getCustomerUsername(), order.getCustomerEmail(), order.getSellerId(),
                        order.getSellerName(), order.getTotalAmount(), order.getCurrency());
            });
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw ApiException.conflict("A payment for this order is already being processed");
        }
    }

    /**
     * Step 2: the payment service reports the result. Successful money moves the reserved stock to sold and
     * confirms the order; if that is no longer possible the order is cancelled and the caller must refund.
     * Reporting the same payment again returns the same answer (idempotent, safe to retry).
     */
    public PaymentOutcomeResponse applyPaymentOutcome(AuthenticatedUser caller, Long orderId,
                                                      PaymentOutcomeRequest outcome) {
        Order order = tx.execute(status -> load(orderId));
        if (!caller.isService() && !order.isOwnedBy(caller.id())) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        String token = CurrentUser.token();
        if (outcome.paymentId() != null && outcome.paymentId().equals(order.getPaymentId())) {
            return replay(order, outcome);
        }
        if (!outcome.successful()) {
            OrderResponse unpaid = tx.execute(status -> {
                Order current = load(orderId);
                if (current.getStatus() == OrderStatus.PENDING) {
                    current.endPaymentAttempt();
                    orderRepository.saveAndFlush(current);
                }
                return OrderMapper.toResponse(current, paymentWindow);
            });
            log.info("Payment for order {} failed: {}", order.getOrderNumber(), outcome.failureReason());
            notifications.toUser(token, order.getUserId(), "PAYMENT_FAILED", "Payment failed for " + order.getOrderNumber(),
                    (outcome.failureReason() == null ? "The payment was declined." : outcome.failureReason())
                            + " You can retry with another payment method.");
            return new PaymentOutcomeResponse(false, false, outcome.failureReason(), unpaid);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Payment {} arrived for order {} which is {} - it must be refunded", outcome.transactionRef(),
                    order.getOrderNumber(), order.getStatus());
            return new PaymentOutcomeResponse(false, "SUCCESS".equals(outcome.status()),
                    "Order " + order.getOrderNumber() + " is " + order.getStatus() + " and no longer awaits payment."
                            + ("SUCCESS".equals(outcome.status()) ? " Your payment has been refunded." : ""),
                    OrderMapper.toResponse(order, paymentWindow));
        }

        // The money is secured: the reserved units are now sold. If that fails, compensate (cancel + refund).
        try {
            inventoryClient.commit(order.getOrderNumber());
        } catch (RuntimeException ex) {
            log.error("Could not commit stock for paid order {} - cancelling it", order.getOrderNumber(), ex);
            return cancelUnconfirmable(orderId, outcome, token);
        }
        PaymentState state = "PENDING".equals(outcome.status()) ? PaymentState.PAY_ON_DELIVERY : PaymentState.PAID;
        OrderResponse confirmed;
        try {
            confirmed = tx.execute(status -> {
                Order current = load(orderId);
                if (current.getStatus() != OrderStatus.PENDING) {
                    return null; // changed while the stock was being committed (handled below)
                }
                current.confirmPayment(outcome.paymentId(), outcome.method(), state, current.getCustomerUsername(),
                        "Payment " + outcome.transactionRef() + " (" + outcome.method() + ")");
                return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
            });
        } catch (ObjectOptimisticLockingFailureException ex) {
            confirmed = null;
        }
        if (confirmed == null) {
            Order current = tx.execute(status -> load(orderId));
            if (outcome.paymentId() != null && outcome.paymentId().equals(current.getPaymentId())) {
                return replay(current, outcome); // the same payment was reported twice at the same time
            }
            log.warn("Order {} changed to {} during payment {} - returning the stock", current.getOrderNumber(),
                    current.getStatus(), outcome.transactionRef());
            safeRelease(current.getOrderNumber());
            return cancelUnconfirmable(orderId, outcome, token);
        }
        log.info("Order {} confirmed ({}, {})", order.getOrderNumber(), outcome.method(), state);
        notifications.toUserWithEmail(token, order.getUserId(), order.getCustomerEmail(), "ORDER_CONFIRMED",
                "Order " + order.getOrderNumber() + " confirmed",
                state == PaymentState.PAID
                        ? "Payment of " + order.getCurrency() + " " + order.getTotalAmount() + " received ("
                        + outcome.maskedInstrument() + "). " + order.getSellerName() + " is preparing your order."
                        : "Cash on delivery: please pay " + order.getCurrency() + " " + order.getTotalAmount()
                        + " when the order arrives.");
        notifications.toUser(token, order.getSellerId(), "NEW_ORDER", "New order " + order.getOrderNumber(),
                order.getCustomerUsername() + " ordered " + order.getUnits() + " unit(s) for " + order.getCurrency() + " "
                        + order.getTotalAmount() + (state == PaymentState.PAID ? " (paid)." : " (cash on delivery).")
                        + " Stock has been updated - please ship the order.");
        return new PaymentOutcomeResponse(true, false, "Order confirmed", confirmed);
    }

    private PaymentOutcomeResponse replay(Order order, PaymentOutcomeRequest outcome) {
        boolean confirmed = order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED;
        boolean refund = !confirmed && "SUCCESS".equals(outcome.status());
        return new PaymentOutcomeResponse(confirmed, refund,
                confirmed ? "Order confirmed" : "Order " + order.getOrderNumber() + " is " + order.getStatus(),
                OrderMapper.toResponse(order, paymentWindow));
    }

    private PaymentOutcomeResponse cancelUnconfirmable(Long orderId, PaymentOutcomeRequest outcome, String token) {
        PaymentState state = "SUCCESS".equals(outcome.status()) ? PaymentState.REFUNDED : PaymentState.VOIDED;
        OrderResponse cancelled = tx.execute(status -> {
            Order current = load(orderId);
            if (current.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
                current.recordPayment(outcome.paymentId(), outcome.method());
                current.cancel("system", "The reserved stock was no longer available after payment", state);
                orderRepository.saveAndFlush(current);
            }
            return OrderMapper.toResponse(current, paymentWindow);
        });
        safeRelease(cancelled.orderNumber());
        String message = "Order " + cancelled.orderNumber() + " could not be confirmed because the reserved stock is "
                + "no longer available." + (state == PaymentState.REFUNDED ? " Your payment has been refunded." : "");
        notifications.toUser(token, cancelled.userId(), "ORDER_CANCELLED",
                "Order " + cancelled.orderNumber() + " cancelled", message);
        return new PaymentOutcomeResponse(false, state == PaymentState.REFUNDED, message, cancelled);
    }

    // ================================================================== cancellation

    /** Customers cancel their own orders before shipping, sellers their own sales, the administrator any order. */
    public OrderResponse cancel(AuthenticatedUser actor, Long orderId, String reason) {
        Order order = tx.execute(status -> load(orderId));
        boolean customer = order.isOwnedBy(actor.id()) && actor.isCustomer();
        boolean seller = actor.isSeller() && order.isSoldBy(actor.id());
        if (!customer && !seller && !actor.isAdmin()) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw ApiException.conflict("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + " and can no longer be cancelled");
        }
        if (order.hasActivePaymentAttempt(paymentAttemptTimeout)) {
            throw ApiException.conflict("A payment is being processed for this order. Please retry in a moment.");
        }
        String finalReason = reason == null || reason.isBlank()
                ? (customer ? "Cancelled by the customer" : seller ? "Cancelled by " + order.getSellerName()
                : "Cancelled by Nexora support")
                : reason.trim();

        // Money first: if the refund fails nothing has changed yet and the cancellation can simply be retried.
        PaymentState newPaymentState = order.getPaymentState();
        if (order.getPaymentState() == PaymentState.PAID || order.getPaymentState() == PaymentState.PAY_ON_DELIVERY) {
            paymentClient.refund(orderId, finalReason);
            newPaymentState = order.getPaymentState() == PaymentState.PAID ? PaymentState.REFUNDED : PaymentState.VOIDED;
        }
        safeRelease(order.getOrderNumber());

        PaymentState paymentState = newPaymentState;
        String actorName = actor.isAdmin() ? "Nexora support" : actor.username();
        OrderResponse cancelled = tx.execute(status -> {
            Order current = load(orderId);
            current.cancel(actorName, finalReason, paymentState);
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        log.info("Order {} cancelled by {}: {}", order.getOrderNumber(), actor.username(), finalReason);
        String token = CurrentUser.token();
        String refund = paymentState == PaymentState.REFUNDED
                ? " Your payment of " + order.getCurrency() + " " + order.getTotalAmount() + " has been refunded." : "";
        notifications.toUserWithEmail(token, order.getUserId(), order.getCustomerEmail(), "ORDER_CANCELLED",
                "Order " + order.getOrderNumber() + " cancelled", finalReason + "." + refund);
        if (!seller) {
            notifications.toUser(token, order.getSellerId(), "ORDER_CANCELLED", "Order " + order.getOrderNumber()
                    + " cancelled", finalReason + ". The stock went back to your inventory.");
        }
        return cancelled;
    }

    // ================================================================== seller fulfilment

    /** One endpoint for the seller's order screen: SHIPPED (carrier + tracking), DELIVERED or CANCELLED. */
    public SellerOrderResponse updateStatus(AuthenticatedUser seller, Long orderId, StatusUpdateRequest request) {
        return switch (request.status()) {
            case SHIPPED -> {
                if (request.carrier() == null || request.carrier().isBlank() || request.trackingNumber() == null
                        || request.trackingNumber().isBlank()) {
                    throw ApiException.badRequest("Carrier and tracking number are required to ship an order");
                }
                yield ship(seller, orderId, request.carrier(), request.trackingNumber(), request.note());
            }
            case DELIVERED -> deliver(seller, orderId);
            case CANCELLED -> {
                cancel(seller, orderId, request.note());
                yield tx.execute(status -> OrderMapper.toSellerResponse(sellerOrder(orderId, seller)));
            }
            default -> throw ApiException.badRequest("Orders can be moved to SHIPPED, DELIVERED or CANCELLED");
        };
    }

    public SellerOrderResponse ship(AuthenticatedUser seller, Long orderId, String carrier, String trackingNumber,
                                    String note) {
        SellerOrderResponse shipped = tx.execute(status -> {
            Order order = sellerOrder(orderId, seller);
            order.ship(carrier.trim(), trackingNumber.trim(), seller.username(), note == null || note.isBlank()
                    ? null : note.trim());
            return OrderMapper.toSellerResponse(orderRepository.saveAndFlush(order));
        });
        notifications.toUserWithEmail(CurrentUser.token(), shipped.customerId(), shipped.customerEmail(),
                "ORDER_SHIPPED", "Order " + shipped.orderNumber() + " shipped",
                seller.displayName() + " shipped your order with " + shipped.carrier() + ". Tracking number: "
                        + shipped.trackingNumber() + ".");
        log.info("Order {} shipped by {} ({} {})", shipped.orderNumber(), seller.username(), carrier, trackingNumber);
        return shipped;
    }

    public SellerOrderResponse deliver(AuthenticatedUser seller, Long orderId) {
        Order order = tx.execute(status -> sellerOrder(orderId, seller));
        if (!order.getStatus().canTransitionTo(OrderStatus.DELIVERED)) {
            throw ApiException.conflict("Order " + order.getOrderNumber() + " must be shipped before it is delivered");
        }
        boolean collectCash = order.getPaymentState() == PaymentState.PAY_ON_DELIVERY;
        if (collectCash) {
            paymentClient.capture(orderId);
        }
        SellerOrderResponse delivered = tx.execute(status -> {
            Order current = sellerOrder(orderId, seller);
            current.deliver(seller.username());
            if (collectCash) {
                current.setPaymentState(PaymentState.PAID);
            }
            return OrderMapper.toSellerResponse(orderRepository.saveAndFlush(current));
        });
        log.info("Order {} delivered{}", delivered.orderNumber(), collectCash ? " (cash collected)" : "");
        notifications.toUserWithEmail(CurrentUser.token(), delivered.customerId(), delivered.customerEmail(),
                "ORDER_DELIVERED", "Order " + delivered.orderNumber() + " delivered",
                "Your order from " + seller.displayName() + " has been delivered. You can now rate the products.");
        return delivered;
    }

    // ================================================================== scheduled

    /** Cancels orders that were not paid in time (inventory releases their reservations on its own as well). */
    public int expireUnpaidOrders(int batchSize) {
        Instant now = Instant.now();
        List<Order> overdue = orderRepository.findExpirable(OrderStatus.PENDING, now.minus(paymentWindow),
                now.minus(paymentAttemptTimeout), Limit.of(batchSize));
        int expired = 0;
        for (Order candidate : overdue) {
            try {
                tx.executeWithoutResult(status -> load(candidate.getId()).cancel("system",
                        "Payment not completed within " + paymentWindow.toMinutes() + " minutes", PaymentState.UNPAID));
                safeRelease(candidate.getOrderNumber());
                notifications.toUser(null, candidate.getUserId(), "ORDER_EXPIRED",
                        "Order " + candidate.getOrderNumber() + " cancelled",
                        "The payment was not completed within " + paymentWindow.toMinutes()
                                + " minutes, so the reserved items were released.");
                expired++;
            } catch (RuntimeException ex) {
                log.warn("Could not expire order {}: {}", candidate.getOrderNumber(), ex.getMessage());
            }
        }
        return expired;
    }

    // ================================================================== helpers

    private void safeRelease(String orderNumber) {
        try {
            inventoryClient.release(orderNumber);
        } catch (RuntimeException ex) {
            log.error("Releasing stock for {} failed - the reservation will expire automatically", orderNumber, ex);
        }
    }

    private boolean isPaymentWindowOver(Order order) {
        return order.getCreatedAt().plus(paymentWindow).isBefore(Instant.now());
    }

    private Order sellerOrder(Long orderId, AuthenticatedUser seller) {
        Order order = load(orderId);
        if (!order.isSoldBy(seller.id())) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        return order;
    }

    private Order load(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
