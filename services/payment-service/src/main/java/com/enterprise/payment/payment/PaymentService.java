package com.enterprise.payment.payment;

import com.enterprise.payment.client.OrderClient.OutcomeRequest;
import com.enterprise.payment.client.OrderClient.PaymentIntent;
import com.enterprise.payment.client.OrderClient.PaymentOutcome;
import com.enterprise.payment.client.OrderClient;
import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.payment.PaymentDtos.CheckoutPaymentRequest;
import com.enterprise.payment.payment.PaymentDtos.CheckoutPaymentResult;
import com.enterprise.payment.payment.PaymentDtos.PayOrderRequest;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.processor.PaymentProcessor;
import com.enterprise.payment.processor.PaymentProcessorFactory;
import com.enterprise.payment.processor.PaymentResult;
import com.enterprise.payment.security.AuthenticatedUser;
import com.enterprise.payment.strategy.PaymentStrategy;
import com.enterprise.payment.strategy.PaymentStrategyFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payment workflow (the payment half of the order saga):
 * <pre>
 * POST /api/payments -> order service: payment intent (amount + one-attempt lock)
 *                    -> PaymentStrategy (card / UPI / cash on delivery) -> PaymentProcessor (mock provider)
 *                    -> save the payment -> order service: outcome -> order CONFIRMED (stock sold)
 *                                                                   or refund (order could not be confirmed)
 * </pre>
 * No database transaction is open during remote calls. If the order service can't be told about a payment, the
 * payment is flagged and {@link OrderUpdateRetryJob} keeps reporting it (the order service handles repeats).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    /** Payments that count as money earned: captured, or cash on delivery still to be collected. */
    public static final List<PaymentStatus> SETTLED = List.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING);
    static final int MAX_ORDER_UPDATE_ATTEMPTS = 30;

    private final PaymentRepository paymentRepository;
    private final PaymentStrategyFactory strategies;
    private final PaymentProcessorFactory processors;
    private final OrderClient orderClient;
    private final TransactionTemplate tx;
    private final BigDecimal feeRate;

    public PaymentService(PaymentRepository paymentRepository, PaymentStrategyFactory strategies,
                          PaymentProcessorFactory processors, OrderClient orderClient, TransactionTemplate tx,
                          @Value("${app.platform.fee-rate:0.05}") BigDecimal feeRate) {
        this.paymentRepository = paymentRepository;
        this.strategies = strategies;
        this.processors = processors;
        this.orderClient = orderClient;
        this.tx = tx;
        this.feeRate = feeRate;
    }

    // ================================================================== paying

    public PaymentResponse pay(AuthenticatedUser customer, PayOrderRequest request) {
        if (!customer.isCustomer()) {
            throw ApiException.forbidden("Only customer accounts can pay for orders");
        }
        PaymentStrategy strategy = strategies.forMethod(request.method());
        strategy.validate(request.details());

        PaymentIntent intent = orderClient.paymentIntent(request.orderId());
        Payment payment;
        try {
            Optional<Payment> settled = paymentRepository.findFirstByOrderIdAndStatusIn(intent.orderId(), SETTLED);
            if (settled.isPresent()) {
                log.info("Order {} already has payment {} - reporting it again instead of charging twice",
                        intent.orderNumber(), settled.get().getTransactionReference());
                return reportToOrderService(settled.get());
            }
            PaymentProcessor processor = processors.processor();
            log.info("Processing {} payment of {} {} for order {}", request.method(), intent.currency(),
                    intent.amount(), intent.orderNumber());
            PaymentResult result = strategy.pay(intent.orderNumber(), intent.amount(), intent.currency(),
                    request.details(), processor);
            payment = tx.execute(status -> paymentRepository.save(new Payment(newTransactionReference(),
                    intent.orderId(), intent.orderNumber(), intent.customerId(), intent.customerUsername(),
                    intent.sellerId(), intent.sellerName(), intent.amount(), intent.currency(), request.method(),
                    result.status(), request.method() == PaymentMethod.COD ? "cash" : processor.name(),
                    result.providerReference(), result.maskedInstrument(), result.failureReason(),
                    platformFee(intent.amount()))));
        } catch (RuntimeException ex) {
            log.error("Payment for order {} could not be processed - releasing the order", intent.orderNumber(), ex);
            releaseOrder(intent);
            throw ex;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.error("Payment failed for {}: {}", payment.getOrderNumber(), payment.getFailureReason());
        } else {
            log.info("Payment {} for order {}: {} {} via {} -> {} (fee {})", payment.getTransactionReference(),
                    payment.getOrderNumber(), payment.getCurrency(), payment.getAmount(), payment.getMethod(),
                    payment.getStatus(), payment.getPlatformFee());
        }
        return reportToOrderService(payment);
    }

    /** Pays every order of one checkout (one order per seller) with the same method; each is charged separately. */
    public List<CheckoutPaymentResult> payCheckout(AuthenticatedUser customer, CheckoutPaymentRequest request) {
        strategies.forMethod(request.method()).validate(request.forOrder(0L).details());
        List<CheckoutPaymentResult> results = new ArrayList<>();
        for (Long orderId : request.orderIds().stream().distinct().toList()) {
            try {
                PaymentResponse payment = pay(customer, request.forOrder(orderId));
                results.add(new CheckoutPaymentResult(orderId, payment.orderConfirmed(),
                        payment.message() != null ? payment.message() : payment.failureReason(), payment));
            } catch (ApiException ex) {
                results.add(new CheckoutPaymentResult(orderId, false, ex.getMessage(), null));
            }
        }
        return results;
    }

    /**
     * Tells the order service how the payment went. A successful payment the order can no longer accept is
     * refunded at once (saga compensation). If the order service is unreachable the payment stays flagged.
     */
    PaymentResponse reportToOrderService(Payment payment) {
        PaymentOutcome outcome;
        try {
            outcome = orderClient.reportOutcome(payment.getOrderId(), new OutcomeRequest(payment.getId(),
                    payment.getTransactionReference(), payment.getMethod().name(), outcomeStatus(payment),
                    payment.getMaskedInstrument(), payment.getFailureReason()));
        } catch (RuntimeException ex) {
            log.warn("Order service not updated about payment {} ({}) - will retry", payment.getTransactionReference(),
                    ex.getMessage());
            Payment flagged = tx.execute(status -> {
                Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
                current.orderUpdateFailed();
                return current;
            });
            return PaymentResponse.from(flagged, "PENDING", false, flagged.getStatus() == PaymentStatus.FAILED
                    ? flagged.getFailureReason()
                    : "Payment received. Your order is being confirmed - you will be notified in a moment.");
        }
        Payment updated = tx.execute(status -> {
            Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
            current.orderUpdated();
            if (outcome.refundRequired() && current.isSettled()) {
                current.refund("Order could not be confirmed: " + outcome.message());
                log.warn("Payment {} refunded: {}", current.getTransactionReference(), outcome.message());
            }
            return current;
        });
        String orderStatus = outcome.order() == null ? null : outcome.order().status();
        String message = outcome.confirmed() ? "Order " + updated.getOrderNumber() + " confirmed"
                : updated.getStatus() == PaymentStatus.FAILED ? updated.getFailureReason() : outcome.message();
        return PaymentResponse.from(updated, orderStatus, outcome.confirmed(), message);
    }

    /** Background retry of payments the order service has not acknowledged (no user token: service key only). */
    public int retryOrderUpdates(int batchSize) {
        List<Payment> waiting = paymentRepository.findAwaitingOrderUpdate(Instant.now().minusSeconds(20),
                MAX_ORDER_UPDATE_ATTEMPTS, Limit.of(batchSize));
        int done = 0;
        for (Payment payment : waiting) {
            PaymentResponse response = reportToOrderService(payment);
            if (!"PENDING".equals(response.orderStatus()) || response.orderConfirmed()) {
                done++;
            }
        }
        return done;
    }

    private void releaseOrder(PaymentIntent intent) {
        try {
            orderClient.reportOutcome(intent.orderId(), new OutcomeRequest(null, null, "CARD", "FAILED", null,
                    "The payment could not be processed. Please try again."));
        } catch (RuntimeException ex) {
            log.warn("Order {} stays locked until the payment attempt times out: {}", intent.orderNumber(),
                    ex.getMessage());
        }
    }

    // ================================================================== refunds and cash collection (order service)

    /** Called by the order service when an order is cancelled by the customer, the seller or the administrator. */
    public PaymentResponse refund(Long orderId, String reason, AuthenticatedUser caller) {
        return tx.execute(status -> {
            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            Payment payment = payments.stream().filter(Payment::isSettled).findFirst()
                    .or(() -> payments.stream().filter(p -> p.getStatus() == PaymentStatus.REFUNDED
                            || p.getStatus() == PaymentStatus.CANCELLED).findFirst())
                    .orElseThrow(() -> ApiException.notFound("No refundable payment for order " + orderId));
            checkAccess(payment, caller);
            if (payment.isSettled()) {
                payment.refund(reason == null ? "Order cancelled" : reason);
                log.info("Payment {} for order {} is now {}", payment.getTransactionReference(),
                        payment.getOrderNumber(), payment.getStatus());
            }
            return PaymentResponse.from(payment);
        });
    }

    /** Cash on delivery collected by the courier; confirmed by the seller when the order is delivered. */
    public PaymentResponse capture(Long orderId, AuthenticatedUser caller) {
        return tx.execute(status -> {
            Payment payment = paymentRepository.findFirstByOrderIdAndStatusIn(orderId, SETTLED)
                    .orElseThrow(() -> ApiException.notFound("No payment to capture for order " + orderId));
            if (!caller.isService() && !caller.isAdmin()
                    && !(caller.isSeller() && caller.id().equals(payment.getSellerId()))) {
                throw ApiException.forbidden("Only the seller can confirm that the cash was collected");
            }
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.capture();
                log.info("Cash collected for order {} ({})", payment.getOrderNumber(),
                        payment.getTransactionReference());
            }
            return PaymentResponse.from(payment);
        });
    }

    // ================================================================== helpers

    public BigDecimal platformFee(BigDecimal amount) {
        return amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal feeRatePercent() {
        return feeRate.movePointRight(2).setScale(1, RoundingMode.HALF_UP);
    }

    /** Customers see their own payments, sellers the payments for their orders, the administrator everything. */
    static void checkAccess(Payment payment, AuthenticatedUser caller) {
        if (!caller.isService() && !caller.isAdmin() && !caller.id().equals(payment.getCustomerId())
                && !caller.id().equals(payment.getSellerId())) {
            throw ApiException.notFound("Payment not found");
        }
    }

    private static String outcomeStatus(Payment payment) {
        return switch (payment.getStatus()) {
            case SUCCESS, REFUNDED -> "SUCCESS";
            case PENDING, CANCELLED -> "PENDING";
            case FAILED -> "FAILED";
        };
    }

    private static String newTransactionReference() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }
}
