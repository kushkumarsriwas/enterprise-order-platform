package com.enterprise.payment.payment;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.ChargeRequest;
import com.enterprise.payment.payment.PaymentDtos.MerchantPaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.MethodTotal;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.PaymentSummary;
import com.enterprise.payment.payment.PaymentDtos.PlatformSummary;
import com.enterprise.payment.processor.PaymentCommand;
import com.enterprise.payment.processor.PaymentProcessorFactory;
import com.enterprise.payment.processor.ProcessorResult;
import com.enterprise.payment.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    /** Payments that count as money earned: captured, or cash on delivery still to be collected. */
    public static final List<PaymentStatus> SETTLED = List.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING);

    private final PaymentRepository paymentRepository;
    private final PaymentProcessorFactory processorFactory;
    private final BigDecimal feeRate;

    public PaymentService(PaymentRepository paymentRepository, PaymentProcessorFactory processorFactory,
                          @Value("${app.platform.fee-rate:0.05}") BigDecimal feeRate) {
        this.paymentRepository = paymentRepository;
        this.processorFactory = processorFactory;
        this.feeRate = feeRate;
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
        String currency = request.currency().toUpperCase(Locale.ROOT);
        ProcessorResult result = processorFactory.forMethod(request.method()).process(new PaymentCommand(
                request.orderNumber(), request.amount(), currency, request.cardNumber(), request.cardHolder(),
                request.expiry(), request.cvv(), request.upiId()));
        Payment payment = paymentRepository.save(new Payment(newTransactionRef(), request.orderId(),
                request.orderNumber(), request.userId(), request.merchantId(), request.amount(), currency,
                request.method(), result.status(), result.providerReference(), result.maskedInstrument(),
                result.failureReason(), platformFee(request.amount())));
        if (result.status() == PaymentStatus.FAILED) {
            log.warn("Payment {} for order {} FAILED: {}", payment.getTransactionRef(), request.orderNumber(),
                    result.failureReason());
        } else {
            log.info("Payment {} for order {}: {} {} via {} -> {} (fee {})", payment.getTransactionRef(),
                    request.orderNumber(), currency, request.amount(), request.method(), result.status(),
                    payment.getPlatformFee());
        }
        return PaymentResponse.from(payment);
    }

    /** Called when an order is cancelled by the customer, the seller or an admin. */
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

    /** Cash on delivery collected by the courier; confirmed by the seller when the order is delivered. */
    @Transactional
    public PaymentResponse capture(Long orderId, AuthenticatedUser caller) {
        Payment payment = paymentRepository.findFirstByOrderIdAndStatusIn(orderId, SETTLED)
                .orElseThrow(() -> ApiException.notFound("No payment to capture for order " + orderId));
        if (!caller.isAdmin() && !(caller.isMerchant() && caller.id().equals(payment.getMerchantId()))) {
            throw ApiException.forbidden("Only the seller can confirm that the cash was collected");
        }
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
        PageRequest pageable = page(page, size);
        if (caller.isAdmin()) {
            return PageResponse.from(paymentRepository.findAll(pageable), PaymentResponse::from);
        }
        return PageResponse.from(caller.isMerchant() ? paymentRepository.findByMerchantId(caller.id(), pageable)
                : paymentRepository.findByUserId(caller.id(), pageable), PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<MerchantPaymentResponse> merchantPayments(Long merchantId, PaymentStatus status, int page,
                                                                  int size) {
        PageRequest pageable = page(page, size);
        return PageResponse.from(status == null ? paymentRepository.findByMerchantId(merchantId, pageable)
                        : paymentRepository.findByMerchantIdAndStatus(merchantId, status, pageable),
                MerchantPaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentSummary merchantSummary(Long merchantId, LocalDate from, LocalDate to) {
        Totals totals = Totals.of(paymentRepository.merchantTotals(merchantId, startOf(from), endOf(to)));
        return new PaymentSummary(from.toString(), to.toString(), "AED", totals.payments, totals.gross,
                totals.collected, totals.awaiting, totals.fees, totals.net, totals.refunded, totals.refunds,
                totals.failed, feeRatePercent(), totals.methods());
    }

    @Transactional(readOnly = true)
    public PlatformSummary platformSummary() {
        Totals totals = Totals.of(paymentRepository.platformTotals());
        return new PlatformSummary("AED", totals.payments, totals.gross, totals.fees, totals.refunded,
                totals.failed, feeRatePercent(), totals.methods());
    }

    public BigDecimal platformFee(BigDecimal amount) {
        return amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal feeRatePercent() {
        return feeRate.movePointRight(2).setScale(1, RoundingMode.HALF_UP);
    }

    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static Instant endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Customers see their own payments, sellers the payments for their orders, admins everything. */
    private static void checkAccess(Payment payment, AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(payment.getUserId())
                && !caller.id().equals(payment.getMerchantId())) {
            throw ApiException.notFound("Payment not found");
        }
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private static String newTransactionRef() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    /** Folds "status, method, count, amount, fee, net" rows into the summary figures. */
    private static final class Totals {
        long payments;
        long refunds;
        long failed;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal awaiting = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        final Map<String, MethodTotal> byMethod = new LinkedHashMap<>();

        static Totals of(List<Object[]> rows) {
            Totals t = new Totals();
            for (Object[] row : rows) {
                PaymentStatus status = (PaymentStatus) row[0];
                PaymentMethod method = (PaymentMethod) row[1];
                long count = ((Number) row[2]).longValue();
                BigDecimal amount = money(row[3]);
                switch (status) {
                    case SUCCESS, PENDING -> {
                        t.payments += count;
                        t.gross = t.gross.add(amount);
                        t.fees = t.fees.add(money(row[4]));
                        t.net = t.net.add(money(row[5]));
                        if (status == PaymentStatus.SUCCESS) {
                            t.collected = t.collected.add(amount);
                        } else {
                            t.awaiting = t.awaiting.add(amount);
                        }
                        t.byMethod.merge(method.name(), new MethodTotal(method.name(), count, amount),
                                (a, b) -> new MethodTotal(a.method(), a.payments() + b.payments(),
                                        a.amount().add(b.amount())));
                    }
                    case REFUNDED -> {
                        t.refunds += count;
                        t.refunded = t.refunded.add(amount);
                    }
                    case FAILED -> t.failed += count;
                    default -> {
                        // CANCELLED: cash on delivery voided before collection - no money moved
                    }
                }
            }
            return t;
        }

        List<MethodTotal> methods() {
            List<MethodTotal> list = new ArrayList<>(byMethod.values());
            list.sort((a, b) -> b.amount().compareTo(a.amount()));
            return list;
        }

        private static BigDecimal money(Object value) {
            if (value == null) {
                return BigDecimal.ZERO;
            }
            BigDecimal decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
