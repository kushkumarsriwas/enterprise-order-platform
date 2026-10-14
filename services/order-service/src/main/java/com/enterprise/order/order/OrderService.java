package com.enterprise.order.order;

import com.enterprise.order.client.InventoryClient;
import com.enterprise.order.client.InventoryClient.ProductInfo;
import com.enterprise.order.client.NotificationPublisher;
import com.enterprise.order.client.PaymentClient;
import com.enterprise.order.client.PaymentClient.ChargeRequest;
import com.enterprise.order.client.PaymentClient.PaymentResult;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.OrderDtos.CheckoutResponse;
import com.enterprise.order.order.OrderDtos.MerchantOrderResponse;
import com.enterprise.order.order.OrderDtos.OrderLineRequest;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.PayManyResult;
import com.enterprise.order.order.OrderDtos.PayOrderRequest;
import com.enterprise.order.order.OrderDtos.PayOrderResponse;
import com.enterprise.order.order.OrderDtos.PaymentSummary;
import com.enterprise.order.order.OrderDtos.PlaceOrderRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saga orchestrator for the marketplace order workflow:
 * <pre>
 * checkout: split cart per merchant -> reserve stock per merchant -> save PENDING orders | compensation: release stock
 * pay:      charge (payment) -> commit stock -> CONFIRMED                            | compensation: refund + cancel
 * fulfil:   merchant ships (carrier + tracking) -> delivers (cash collected for COD)
 * cancel:   refund (payment) -> release stock -> CANCELLED
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

    // ------------------------------------------------------------------ checkout

    public CheckoutResponse placeOrder(AuthenticatedUser customer, PlaceOrderRequest request) {
        if (!customer.isCustomer()) {
            throw ApiException.forbidden("Only customer accounts can place orders");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : request.items()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        // 1) Which merchant sells each product? (one order per merchant)
        Map<Long, ProductInfo> products = inventoryClient.lookup(new ArrayList<>(quantities.keySet())).stream()
                .collect(Collectors.toMap(ProductInfo::productId, Function.identity()));
        Map<Long, List<InventoryClient.Item>> byMerchant = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            ProductInfo product = products.get(entry.getKey());
            if (product == null || !product.purchasable()) {
                throw ApiException.conflict("Product " + (product == null ? entry.getKey() : "'" + product.name() + "'")
                        + " is no longer available. Please remove it from your cart.");
            }
            byMerchant.computeIfAbsent(product.merchantId(), id -> new ArrayList<>())
                    .add(new InventoryClient.Item(entry.getKey(), entry.getValue()));
        }

        // 2) Reserve stock for every merchant's order; if one fails, release the ones already reserved.
        List<InventoryClient.Reservation> reservations = new ArrayList<>();
        try {
            for (List<InventoryClient.Item> items : byMerchant.values()) {
                reservations.add(inventoryClient.reserve(OrderNumberGenerator.next(), items));
            }
        } catch (RuntimeException ex) {
            reservations.forEach(reservation -> safeRelease(reservation.orderRef()));
            throw ex;
        }

        // 3) Save all orders in ONE local transaction; if that fails, compensate by releasing all reservations.
        List<Order> saved;
        try {
            saved = tx.execute(status -> reservations.stream().map(reservation -> {
                InventoryClient.Line first = reservation.lines().get(0);
                Order order = new Order(reservation.orderRef(), customer.id(), customer.username(), customer.email(),
                        blankToNull(request.phone()), first.merchantId(), first.merchantName(), CURRENCY,
                        request.shippingAddress().trim());
                reservation.lines().forEach(line -> order.addItem(new OrderItem(line.productId(), line.sku(),
                        line.name(), line.unitPrice(), line.unitCost(), line.quantity())));
                return orderRepository.save(order);
            }).toList());
        } catch (RuntimeException ex) {
            log.error("Saving checkout of {} failed - releasing reserved stock", customer.username(), ex);
            reservations.forEach(reservation -> safeRelease(reservation.orderRef()));
            throw ex;
        }
        List<OrderResponse> orders = tx.execute(status -> saved.stream()
                .map(order -> OrderMapper.toResponse(order, paymentWindow)).toList());
        BigDecimal grandTotal = orders.stream().map(OrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String token = CurrentUser.token();
        notifications.toUser(token, customer.id(), "ORDER_PLACED",
                orders.size() == 1 ? "Order " + orders.get(0).orderNumber() + " placed" : orders.size() + " orders placed",
                "Total " + CURRENCY + " " + grandTotal + ". Please complete the payment within "
                        + paymentWindow.toMinutes() + " minutes - your items are reserved until then.");
        log.info("Checkout by {}: {} order(s), total {} {}", customer.username(), orders.size(), grandTotal, CURRENCY);
        return new CheckoutResponse(orders, grandTotal, CURRENCY);
    }

    // ------------------------------------------------------------------ payment

    public PayOrderResponse pay(AuthenticatedUser customer, Long orderId, PayOrderRequest request) {
        // Step 1: flag "payment in progress". The @Version column makes a concurrent second click fail (409),
        // so the same order can never be charged twice.
        Order order = tx.execute(status -> {
            Order current = load(orderId);
            if (!current.isOwnedBy(customer.id())) {
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
                    order.getMerchantId(), order.getTotalAmount(), order.getCurrency(), request.method(),
                    request.cardNumber(), request.cardHolder(), request.expiry(), request.cvv(), request.upiId()));
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
            notifications.toUser(token, customer.id(), "PAYMENT_FAILED", "Payment failed for " + order.getOrderNumber(),
                    result.failureReason() + " You can retry with another payment method.");
            return new PayOrderResponse(unpaid, summary);
        }

        // Step 3: stock is now sold. If this fails, compensate: refund and cancel.
        try {
            inventoryClient.commit(order.getOrderNumber());
        } catch (RuntimeException ex) {
            log.error("Could not commit stock for paid order {} - refunding", order.getOrderNumber(), ex);
            compensatePaidOrder(orderId, token, customer.id());
            throw ApiException.conflict("Order " + order.getOrderNumber()
                    + " could not be confirmed because the reserved stock is no longer available. "
                    + "Your payment has been refunded.");
        }

        // Step 4: confirm the order and tell both sides.
        PaymentState state = result.isPayOnDelivery() ? PaymentState.PAY_ON_DELIVERY : PaymentState.PAID;
        OrderResponse confirmed = tx.execute(status -> {
            Order current = load(orderId);
            current.confirmPayment(result.id(), result.method(), state, customer.username(),
                    "Payment " + result.transactionRef() + " (" + result.method() + ")");
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        log.info("Order {} confirmed ({}, {})", order.getOrderNumber(), result.method(), state);
        notifications.toUser(token, customer.id(), "ORDER_CONFIRMED", "Order " + order.getOrderNumber() + " confirmed",
                state == PaymentState.PAID
                        ? "Payment of " + order.getCurrency() + " " + order.getTotalAmount() + " received ("
                        + result.maskedInstrument() + "). " + order.getMerchantName() + " is preparing your order."
                        : "Cash on delivery: please pay " + order.getCurrency() + " " + order.getTotalAmount()
                        + " when the order arrives.");
        notifications.toUser(token, order.getMerchantId(), "NEW_ORDER", "New order " + order.getOrderNumber(),
                customer.username() + " ordered " + order.getItems().size() + " item(s) for " + order.getCurrency() + " "
                        + order.getTotalAmount() + (state == PaymentState.PAID ? " (paid)." : " (cash on delivery).")
                        + " Stock has been updated - please ship the order.");
        return new PayOrderResponse(confirmed, summary);
    }

    /** Pays several orders of one checkout with the same payment method (one charge per merchant order). */
    public List<PayManyResult> payMany(AuthenticatedUser customer, List<Long> orderIds, PayOrderRequest request) {
        List<PayManyResult> results = new ArrayList<>();
        for (Long orderId : orderIds) {
            try {
                PayOrderResponse response = pay(customer, orderId, request);
                boolean ok = !"FAILED".equals(response.payment().status());
                results.add(new PayManyResult(orderId, ok, ok ? "Confirmed" : response.payment().failureReason(), response));
            } catch (ApiException ex) {
                results.add(new PayManyResult(orderId, false, ex.getMessage(), null));
            }
        }
        return results;
    }

    // ------------------------------------------------------------------ cancellation

    /** Customers cancel their own orders before shipping; merchants their own sales; admins anything. */
    public OrderResponse cancel(AuthenticatedUser actor, Long orderId, String reason) {
        Order order = tx.execute(status -> load(orderId));
        boolean customer = order.isOwnedBy(actor.id());
        boolean merchant = actor.isMerchant() && order.isSoldBy(actor.id());
        if (!customer && !merchant && !actor.isAdmin()) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw ApiException.conflict("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + " and can no longer be cancelled");
        }
        if (order.isPaymentInProgress()) {
            throw ApiException.conflict("A payment is being processed for this order. Please retry in a moment.");
        }
        String finalReason = reason == null || reason.isBlank()
                ? (customer ? "Cancelled by customer" : merchant ? "Cancelled by " + order.getMerchantName()
                : "Cancelled by marketplace support")
                : reason.trim();

        // Money first: if the refund fails nothing has changed and the cancellation can be retried.
        PaymentState newPaymentState = order.getPaymentState();
        if (order.getPaymentState() == PaymentState.PAID || order.getPaymentState() == PaymentState.PAY_ON_DELIVERY) {
            paymentClient.refund(orderId, finalReason);
            newPaymentState = order.getPaymentState() == PaymentState.PAID ? PaymentState.REFUNDED : PaymentState.VOIDED;
        }
        safeRelease(order.getOrderNumber());

        PaymentState paymentState = newPaymentState;
        OrderResponse cancelled = tx.execute(status -> {
            Order current = load(orderId);
            current.cancel(actor.username(), finalReason, paymentState);
            return OrderMapper.toResponse(orderRepository.saveAndFlush(current), paymentWindow);
        });
        log.info("Order {} cancelled by {}: {}", order.getOrderNumber(), actor.username(), finalReason);
        String token = CurrentUser.token();
        String refund = paymentState == PaymentState.REFUNDED
                ? " Your payment of " + order.getCurrency() + " " + order.getTotalAmount() + " has been refunded." : "";
        notifications.toUser(token, order.getUserId(), "ORDER_CANCELLED", "Order " + order.getOrderNumber()
                + " cancelled", finalReason + "." + refund);
        if (!merchant) {
            notifications.toUser(token, order.getMerchantId(), "ORDER_CANCELLED", "Order " + order.getOrderNumber()
                    + " cancelled", finalReason + ". The reserved stock went back to your inventory.");
        }
        return cancelled;
    }

    // ------------------------------------------------------------------ merchant fulfilment

    public MerchantOrderResponse ship(AuthenticatedUser merchant, Long orderId, String carrier, String trackingNumber,
                                      String note) {
        MerchantOrderResponse shipped = tx.execute(status -> {
            Order order = merchantOrder(orderId, merchant);
            order.ship(carrier.trim(), trackingNumber.trim(), merchant.username(), note == null || note.isBlank()
                    ? null : note.trim());
            return OrderMapper.toMerchantResponse(orderRepository.saveAndFlush(order));
        });
        notifications.toUser(CurrentUser.token(), shipped.customerId(), "ORDER_SHIPPED",
                "Order " + shipped.orderNumber() + " shipped",
                merchant.displayName() + " shipped your order with " + shipped.carrier() + ". Tracking number: "
                        + shipped.trackingNumber() + ".");
        log.info("Order {} shipped by {} ({} {})", shipped.orderNumber(), merchant.username(), carrier, trackingNumber);
        return shipped;
    }

    public MerchantOrderResponse deliver(AuthenticatedUser merchant, Long orderId) {
        Order order = tx.execute(status -> merchantOrder(orderId, merchant));
        if (!order.getStatus().canTransitionTo(OrderStatus.DELIVERED)) {
            throw ApiException.conflict("Order " + order.getOrderNumber() + " must be shipped before it is delivered");
        }
        boolean collectCash = order.getPaymentState() == PaymentState.PAY_ON_DELIVERY;
        if (collectCash) {
            paymentClient.capture(orderId);
        }
        MerchantOrderResponse delivered = tx.execute(status -> {
            Order current = merchantOrder(orderId, merchant);
            current.deliver(merchant.username());
            if (collectCash) {
                current.setPaymentState(PaymentState.PAID);
            }
            return OrderMapper.toMerchantResponse(orderRepository.saveAndFlush(current));
        });
        notifications.toUser(CurrentUser.token(), delivered.customerId(), "ORDER_DELIVERED",
                "Order " + delivered.orderNumber() + " delivered",
                "Your order from " + merchant.displayName() + " has been delivered. You can now rate the products.");
        return delivered;
    }

    // ------------------------------------------------------------------ queries

    public OrderResponse get(AuthenticatedUser user, Long orderId) {
        return tx.execute(status -> {
            Order order = load(orderId);
            if (!user.isAdmin() && !order.isOwnedBy(user.id())) {
                throw ApiException.notFound("Order " + orderId + " not found");
            }
            return OrderMapper.toResponse(order, paymentWindow);
        });
    }

    public MerchantOrderResponse getForMerchant(AuthenticatedUser merchant, Long orderId) {
        return tx.execute(status -> OrderMapper.toMerchantResponse(merchantOrder(orderId, merchant)));
    }

    public PageResponse<OrderResponse> list(AuthenticatedUser user, Long customerId, OrderStatus status, int page,
                                            int size) {
        Long filterUser = user.isAdmin() ? customerId : user.id();
        if (!user.isAdmin() && customerId != null && !customerId.equals(user.id())) {
            throw ApiException.forbidden("You can only view your own orders");
        }
        PageRequest pageable = newestFirst(page, size);
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

    public PageResponse<MerchantOrderResponse> listForMerchant(AuthenticatedUser merchant, OrderStatus status, int page,
                                                               int size) {
        PageRequest pageable = newestFirst(page, size);
        return tx.execute(txStatus -> PageResponse.from(status == null
                        ? orderRepository.findByMerchantId(merchant.id(), pageable)
                        : orderRepository.findByMerchantIdAndStatus(merchant.id(), status, pageable),
                OrderMapper::toMerchantResponse));
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

    // ------------------------------------------------------------------ helpers

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

    private Order merchantOrder(Long orderId, AuthenticatedUser merchant) {
        Order order = load(orderId);
        if (!order.isSoldBy(merchant.id())) {
            throw ApiException.notFound("Order " + orderId + " not found");
        }
        return order;
    }

    private Order load(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
    }

    private static PageRequest newestFirst(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
