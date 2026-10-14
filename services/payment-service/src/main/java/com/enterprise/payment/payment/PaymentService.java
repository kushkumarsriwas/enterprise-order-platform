package com.enterprise.payment.payment;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.ChargeRequest;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.processor.PaymentCommand;
import com.enterprise.payment.processor.PaymentProcessorFactory;
import com.enterprise.payment.processor.ProcessorResult;
import com.enterprise.payment.security.AuthenticatedUser;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final List<PaymentStatus> SETTLED = List.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING);

    private final PaymentRepository paymentRepository;
    private final PaymentProcessorFactory processorFactory;

    public PaymentService(PaymentRepository paymentRepository, PaymentProcessorFactory processorFactory) {
        this.paymentRepository = paymentRepository;
        this.processorFactory = processorFactory;
    }

    /**
     * Idempotent: if the order already has a successful (or cash-on-delivery) payment, that payment is
     * returned instead of charging the customer again.
     */
    @Transactional
    public PaymentResponse charge(ChargeRequest request, AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(request.userId())) {
            throw ApiException.forbidden("Payments can only be made for your own orders");
        }
        Optional<Payment> settled = paymentRepository.findFirstByOrderIdAndStatusIn(request.orderId(), SETTLED);
        if (settled.isPresent()) {
            log.info("Order {} already has payment {} - not charging again", request.orderNumber(),
                    settled.get().getTransactionRef());
            return PaymentResponse.from(settled.get());
        }
        ProcessorResult result = processorFactory.forMethod(request.method()).process(new PaymentCommand(
                request.orderNumber(), request.amount(), request.currency().toUpperCase(Locale.ROOT),
                request.cardNumber(), request.cardHolder(), request.expiry(), request.cvv(), request.upiId()));
        Payment payment = paymentRepository.save(new Payment(newTransactionRef(), request.orderId(),
                request.orderNumber(), request.userId(), request.amount(), request.currency().toUpperCase(Locale.ROOT),
                request.method(), result.status(), result.providerReference(), result.maskedInstrument(),
                result.failureReason()));
        if (result.status() == PaymentStatus.FAILED) {
            log.warn("Payment {} for order {} FAILED: {}", payment.getTransactionRef(), request.orderNumber(),
                    result.failureReason());
        } else {
            log.info("Payment {} for order {}: {} {} via {} -> {}", payment.getTransactionRef(), request.orderNumber(),
                    request.currency(), request.amount(), request.method(), result.status());
        }
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refund(Long orderId, String reason, AuthenticatedUser caller) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        Payment payment = payments.stream().filter(p -> SETTLED.contains(p.getStatus())).findFirst()
                .or(() -> payments.stream().filter(p -> p.getStatus() == PaymentStatus.REFUNDED
                        || p.getStatus() == PaymentStatus.CANCELLED).findFirst())
                .orElseThrow(() -> ApiException.notFound("No refundable payment for order " + orderId));
        checkAccess(payment, caller);
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.PENDING) {
            payment.refund(reason == null ? "Order cancelled" : reason);
            log.info("Payment {} for order {} is now {}", payment.getTransactionRef(), payment.getOrderNumber(),
                    payment.getStatus());
        }
        return PaymentResponse.from(payment);
    }

    /** Cash on delivery collected by the courier. */
    @Transactional
    public PaymentResponse capture(Long orderId) {
        Payment payment = paymentRepository.findFirstByOrderIdAndStatusIn(orderId, SETTLED)
                .orElseThrow(() -> ApiException.notFound("No payment to capture for order " + orderId));
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.capture();
            log.info("Cash collected for order {} ({})", payment.getOrderNumber(), payment.getTransactionRef());
        }
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id, AuthenticatedUser caller) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Payment " + id + " not found"));
        checkAccess(payment, caller);
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forOrder(Long orderId, AuthenticatedUser caller) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        payments.forEach(payment -> checkAccess(payment, caller));
        return payments.stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(AuthenticatedUser caller, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(caller.isAdmin() ? paymentRepository.findAll(pageable)
                : paymentRepository.findByUserId(caller.id(), pageable), PaymentResponse::from);
    }

    private static void checkAccess(Payment payment, AuthenticatedUser caller) {
        if (!caller.isAdmin() && !payment.getUserId().equals(caller.id())) {
            throw ApiException.notFound("Payment not found");
        }
    }

    private static String newTransactionRef() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }
}
