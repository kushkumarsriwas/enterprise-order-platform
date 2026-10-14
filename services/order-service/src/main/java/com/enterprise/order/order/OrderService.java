package com.enterprise.order.order;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.client.PaymentClient;
import com.enterprise.order.client.PaymentClient.ChargeRequest;
import com.enterprise.order.client.PaymentClient.PaymentResult;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.OrderDtos.OrderLineRequest;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.PayOrderRequest;
import com.enterprise.order.order.OrderDtos.PayOrderResponse;
import com.enterprise.order.order.OrderDtos.PaymentSummary;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
import com.enterprise.order.security.AuthenticatedUser;
import com.enterprise.order.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saga orchestrator for the order workflow:
 * <pre>
 * place:  reserve stock (inventory) -> save PENDING order      | compensation: release stock
 * pay:    charge (payment) -> commit stock -> CONFIRMED         | compensation: refund + cancel
 * cancel: refund (payment) -> release stock -> CANCELLED
 * </pre>
 * Remote calls are made outside database transactions, so no DB connection is held while waiting on the network.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String CURRENCY = "AED";

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final NotificationPublisher notifications;
    private final TransactionTemplate tx;
    private final Duration paymentWindow;

    public OrderService(OrderRepository orderRepository, InventoryClient inventoryClient, PaymentClient paymentClient,
                        NotificationPublisher notifications, TransactionTemplate tx,
                        @Value("${app.orders.payment-window:30m}") Duration paymentWindow) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.notifications = notifications;
        this.tx = tx;
        this.paymentWindow = paymentWindow;
    }

    public OrderResponse placeOrder(AuthenticatedUser user, PlaceOrderRequest request) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : request.items()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        String orderNumber = OrderNumberGenerator.next();
        log.info("Placing order {} for user {} ({} product(s))", orderNumber, user.username(), quantities.size());

        // Step 1: reserve stock. Prices come from the inventory service, never from the client.
        InventoryClient.Reservation reservation = inventoryClient.reserve(orderNumber, quantities.entrySet().stream()
                .map(entry -> new InventoryClient.Item(entry.getKey(), entry.getValue())).toList());

        // Step 2: persist the order. If that fails, compensate by releasing the reservation.
        Order saved;
        try {
            saved = tx.execute(status -> {
                Order order = new Order(orderNumber, user.id(), user.username(), user.email(), CURRENCY,
                        request.shippingAddress().trim());
                reservation.lines().forEach(line -> order.addItem(new OrderItem(line.productId(), line.sku(),
                        line.name(), line.unitPrice(), line.quantity())));
                return orderRepository.save(order);
            });
        } catch (RuntimeException ex) {
            log.error("Saving order {} failed - releasing reserved stock", orderNumber, ex);
            safeRelease(orderNumber);
            throw ex;
        }
        String token = CurrentUser.token();
        notifications.toUser(token, user.id(), "ORDER_PLACED", "Order " + orderNumber + " placed",
                "Total " + CURRENCY + " " + saved.getTotalAmount() + ". Please complete the payment within "
                        + paymentWindow.toMinutes() + " minutes.");
        notifications.toAdmins(token, "NEW_ORDER", "New order " + orderNumber,
                user.username() + " placed an order of " + CURRENCY + " " + saved.getTotalAmount() + ".");
        log.info("Order {} saved with total {} {}", orderNumber, saved.getTotalAmount(), CURRENCY);
        return OrderMapper.toResponse(saved, paymentWindow);
    }

    public PayOrderResponse pay(AuthenticatedUser user, Long orderId, PayOrderRequest request) {
        // Step 1: flag "payment in progress". The @Version column makes a concurrent second click fail (409),
        // so the same order can never be charged twice.
        Order order = tx.execute(status -> {
            Order current = load(orderId);
            if (!current.isOwnedBy(user.id())) {
                throw ApiException.forbidden("You can only pay for your own orders");
            }
            if (current.getStatus() != OrderStatus.PENDING) {
                throw ApiException.conflict("Order " + current.getOrderNumber() + " is " + current.getStatus()
                        + " and cannot be paid");
            }
            if (isPaymentWindowOver(current)) {
                throw ApiException.conflict("The payment window for order " + current.getOrderNumber()
                        + " has expired. Please place a new order.");
            }
            if (current.isPaymentInProgress()) {
                throw ApiException.conflict("A payment for this order is already being processed");
            }
            current.setPaymentInProgress(true);
            return orderRepository.saveAndFlush(current);
        });

        // Step 2: charge through the payment service (strategy pattern lives there).
        PaymentResult result;
        try {
            result = paymentClient.charge(new ChargeRequest(order.getId(), order.getOrderNumber(), order.getUserId(),
                    order.getTotalAmount(), order.getCurrency(), request.method(), request.cardNumber(),
                    request.cardHolder(), request.expiry(), request.cvv(), request.upiId()));
        } catch (RuntimeException ex) {
            clearPaymentFlag(orderId);
            throw ex;
        }
        String token = CurrentUser.token();
        PaymentSummary summary = new PaymentSummary(result.id(), result.transactionRef(), result.method(),
                result.status(), result.maskedInstrument(), result.failureReason());

        if (!result.isSuccessful() && !result.isPayOnDelivery()) {
            OrderResponse unpaid = clearPaymentFlag(orderId);
            log.info("Payment for order {} failed: {}", order.getOrderNumber(), result.failureReason());
            notifications.toUser(token, user.id(), "PAYMENT_FAILED", "Payment failed for " + order.getOrderNumber(),
                    result.failureReason() + " You can retry with another payment method.");
            return new PayOrderResponse(unpaid, summary);
        }

        // Step 3: stock is now sold. If this fails, compensate: refund and cancel.
        try {
            inventoryClient.commit(order.getOrderNumber());
        } catch (RuntimeException ex) {
            log.error("Could not commit stock for paid order {} - refunding", order.getOrderNumber(), ex);
            compensatePaidOrder(orderId, token, user.id());
            throw ApiException.conflict("Order " + order.getOrderNumber()
                    + " could not be confirmed because the reserved stock is no longer available. "
                    + "Your payment has been refunded.");
        }

        // Step 4: confirm the order.
        PaymentState state = result.isPayOnDelivery() ? PaymentState.PAY_ON_DELIVERY : PaymentState.PAID;
        OrderResponse confirmed = tx.execute(status -> {
            Order current = load(orderId);
            current.confirmPayment(result.id(), result.method(), state, user.username(),
                    "Payment " + result.transactionRef() + " (" + result.method() + ")");
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        log.info("Order {} confirmed ({}, {})", order.getOrderNumber(), result.method(), state);
        notifications.toUser(token, user.id(), "ORDER_CONFIRMED", "Order " + order.getOrderNumber() + " confirmed",
                state == PaymentState.PAID
                        ? "Payment of " + order.getCurrency() + " " + order.getTotalAmount() + " received ("
                        + result.maskedInstrument() + "). We are preparing your order."
                        : "Cash on delivery: please pay " + order.getCurrency() + " " + order.getTotalAmount()
                        + " when the order arrives.");
        return new PayOrderResponse(confirmed, summary);
    }

    public OrderResponse cancel(AuthenticatedUser user, Long orderId, String reason) {
        Order order = tx.execute(status -> load(orderId));
        if (!user.isAdmin() && !order.isOwnedBy(user.id())) {
            throw ApiException.forbidden("You can only cancel your own orders");
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw ApiException.conflict("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + " and can no longer be cancelled");
        }
        if (order.isPaymentInProgress()) {
            throw ApiException.conflict("A payment is being processed for this order. Please retry in a moment.");
        }
        String finalReason = reason == null || reason.isBlank()
                ? (user.isAdmin() && !order.isOwnedBy(user.id()) ? "Cancelled by store" : "Cancelled by customer")
                : reason.trim();

        // Money first: if the refund fails nothing has changed and the user can retry.
        PaymentState newPaymentState = order.getPaymentState();
        if (order.getPaymentState() == PaymentState.PAID || order.getPaymentState() == PaymentState.PAY_ON_DELIVERY) {
            paymentClient.refund(orderId, finalReason);
            newPaymentState = order.getPaymentState() == PaymentState.PAID ? PaymentState.REFUNDED : PaymentState.VOIDED;
        }
        safeRelease(order.getOrderNumber());

        PaymentState paymentState = newPaymentState;
        OrderResponse cancelled = tx.execute(status -> {
            Order current = load(orderId);
            current.cancel(user.username(), finalReason, paymentState);
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        log.info("Order {} cancelled by {}: {}", order.getOrderNumber(), user.username(), finalReason);
        String token = CurrentUser.token();
        notifications.toUser(token, order.getUserId(), "ORDER_CANCELLED", "Order " + order.getOrderNumber()
                + " cancelled", finalReason + (paymentState == PaymentState.REFUNDED
                ? ". Your payment of " + order.getCurrency() + " " + order.getTotalAmount() + " has been refunded." : "."));
        if (!user.isAdmin()) {
            notifications.toAdmins(token, "ORDER_CANCELLED", "Order " + order.getOrderNumber() + " cancelled",
                    user.username() + " cancelled the order: " + finalReason);
        }
        return cancelled;
    }

    /** Admin fulfilment: CONFIRMED -> SHIPPED -> DELIVERED (cash is collected on delivery for COD). */
    public OrderResponse updateStatus(AuthenticatedUser admin, Long orderId, OrderStatus next, String note) {
        if (next == OrderStatus.CANCELLED) {
            return cancel(admin, orderId, note);
        }
        if (next == OrderStatus.PENDING || next == OrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Orders become CONFIRMED only through a successful payment");
        }
        Order order = tx.execute(status -> load(orderId));
        if (!order.getStatus().canTransitionTo(next)) {
            throw ApiException.conflict("Order " + order.getOrderNumber() + " cannot change from "
                    + order.getStatus() + " to " + next);
        }
        boolean collectCash = next == OrderStatus.DELIVERED && order.getPaymentState() == PaymentState.PAY_ON_DELIVERY;
        if (collectCash) {
            paymentClient.capture(orderId);
        }
        OrderResponse updated = tx.execute(status -> {
            Order current = load(orderId);
            current.changeStatus(next, admin.username(), note == null || note.isBlank() ? null : note.trim());
            if (collectCash) {
                current.setPaymentState(PaymentState.PAID);
            }
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        String message = next == OrderStatus.SHIPPED
                ? "Your order is on the way." + (note == null || note.isBlank() ? "" : " " + note.trim())
                : "Your order has been delivered. Thank you for shopping with us!";
        notifications.toUser(CurrentUser.token(), order.getUserId(), "ORDER_" + next.name(),
                "Order " + order.getOrderNumber() + " " + next.name().toLowerCase(), message);
        log.info("Order {} moved to {} by {}", order.getOrderNumber(), next, admin.username());
        return updated;
    }

    public OrderResponse get(AuthenticatedUser user, Long orderId) {
        return tx.execute(status -> {
            Order order = load(orderId);
            if (!user.isAdmin() && !order.isOwnedBy(user.id())) {
                throw ApiException.notFound("Order " + orderId + " not found");
            }
            return OrderMapper.toResponse(order, paymentWindow);
        });
    }

    public PageResponse<OrderResponse> list(AuthenticatedUser user, Long customerId, OrderStatus status, int page,
                                            int size) {
        Long filterUser = user.isAdmin() ? customerId : user.id();
        if (!user.isAdmin() && customerId != null && !customerId.equals(user.id())) {
            throw ApiException.forbidden("You can only view your own orders");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return tx.execute(txStatus -> {
            Page<Order> result;
            if (filterUser != null) {
                result = status == null ? orderRepository.findByUserId(filterUser, pageable)
                        : orderRepository.findByUserIdAndStatus(filterUser, status, pageable);
            } else {
                result = status == null ? orderRepository.findAll(pageable)
                        : orderRepository.findByStatus(status, pageable);
            }
            return PageResponse.from(result, order -> OrderMapper.toResponse(order, paymentWindow));
        });
    }

    /** Scheduled: cancel orders that were not paid in time (inventory releases their stock on its own). */
    public int expireUnpaidOrders(int batchSize) {
        Instant cutoff = Instant.now().minus(paymentWindow);
        List<Order> overdue = orderRepository.findByStatusAndPaymentInProgressFalseAndCreatedAtBefore(
                OrderStatus.PENDING, cutoff, Limit.of(batchSize));
        int expired = 0;
        for (Order candidate : overdue) {
            try {
                tx.executeWithoutResult(status -> load(candidate.getId()).cancel("system",
                        "Payment not completed within " + paymentWindow.toMinutes() + " minutes", PaymentState.UNPAID));
                expired++;
            } catch (RuntimeException ex) {
                log.warn("Could not expire order {}: {}", candidate.getOrderNumber(), ex.getMessage());
            }
        }
        return expired;
    }

    private void compensatePaidOrder(Long orderId, String token, Long userId) {
        PaymentState finalState = PaymentState.REFUNDED;
        try {
            paymentClient.refund(orderId, "Stock could not be confirmed");
        } catch (RuntimeException refundError) {
            finalState = PaymentState.PAID;
            log.error("MANUAL ACTION NEEDED: refund for order {} failed", orderId, refundError);
        }
        PaymentState state = finalState;
        OrderResponse cancelled = tx.execute(status -> {
            Order current = load(orderId);
            current.cancel("system", "Stock could not be confirmed after payment", state);
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        notifications.toUser(token, userId, "ORDER_CANCELLED", "Order " + cancelled.orderNumber() + " cancelled",
                "The reserved stock was no longer available. Your payment has been refunded.");
    }

    private OrderResponse clearPaymentFlag(Long orderId) {
        return tx.execute(status -> {
            Order current = load(orderId);
            current.setPaymentInProgress(false);
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
    }

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

    private Order load(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
    }
}
