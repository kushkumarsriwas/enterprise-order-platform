package com.enterprise.payment.payment;

import com.enterprise.payment.common.ApiException;
import com.enterprise.payment.common.PageResponse;
import com.enterprise.payment.payment.PaymentDtos.AdminPaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.DailyVolume;
import com.enterprise.payment.payment.PaymentDtos.MethodTotal;
import com.enterprise.payment.payment.PaymentDtos.PaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.PaymentSummary;
import com.enterprise.payment.payment.PaymentDtos.PlatformSummary;
import com.enterprise.payment.payment.PaymentDtos.SellerPaymentResponse;
import com.enterprise.payment.payment.PaymentDtos.SellerVolume;
import com.enterprise.payment.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of payments: customer history, seller earnings and the administrator's transaction explorer. */
@Service
@Transactional(readOnly = true)
public class PaymentQueryService {

    public static final int EXPORT_LIMIT = 5000;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public PaymentQueryService(PaymentRepository paymentRepository, PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    public PaymentResponse get(Long id, AuthenticatedUser caller) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Payment " + id + " not found"));
        PaymentService.checkAccess(payment, caller);
        return PaymentResponse.from(payment);
    }

    public AdminPaymentResponse getForAdmin(Long id) {
        return paymentRepository.findById(id).map(AdminPaymentResponse::from)
                .orElseThrow(() -> ApiException.notFound("Payment " + id + " not found"));
    }

    public List<PaymentResponse> forOrder(Long orderId, AuthenticatedUser caller) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        payments.forEach(payment -> PaymentService.checkAccess(payment, caller));
        return payments.stream().map(PaymentResponse::from).toList();
    }

    public PageResponse<PaymentResponse> mine(AuthenticatedUser customer, int page, int size) {
        return PageResponse.from(paymentRepository.findByCustomerId(customer.id(), page(page, size,
                PaymentFilter.sort(null))), PaymentResponse::from);
    }

    public PageResponse<SellerPaymentResponse> forSeller(PaymentFilter filter, String sort, int page, int size) {
        return PageResponse.from(paymentRepository.findAll(filter.toSpecification(), page(page, size,
                PaymentFilter.sort(sort))), SellerPaymentResponse::from);
    }

    public PageResponse<AdminPaymentResponse> forAdmin(PaymentFilter filter, String sort, int page, int size) {
        return PageResponse.from(paymentRepository.findAll(filter.toSpecification(), page(page, size,
                PaymentFilter.sort(sort))), AdminPaymentResponse::from);
    }

    public List<AdminPaymentResponse> export(PaymentFilter filter, String sort) {
        return paymentRepository.findAll(filter.toSpecification(), PageRequest.of(0, EXPORT_LIMIT,
                PaymentFilter.sort(sort))).map(AdminPaymentResponse::from).getContent();
    }

    public PaymentSummary sellerSummary(Long sellerId, LocalDate from, LocalDate to) {
        Totals t = Totals.of(paymentRepository.totals(sellerId, startOf(from), endOf(to)));
        return new PaymentSummary(from.toString(), to.toString(), "AED", t.payments, t.gross, t.collected,
                t.awaiting, t.fees, t.net, t.refunded, t.refunds, t.failed, t.successRate(),
                paymentService.feeRatePercent(), t.methods(), daily(sellerId, from, to));
    }

    public PlatformSummary platformSummary(LocalDate from, LocalDate to) {
        Totals t = Totals.of(paymentRepository.totals(null, startOf(from), endOf(to)));
        List<SellerVolume> sellers = new ArrayList<>();
        for (Object[] row : paymentRepository.bySeller(PaymentService.SETTLED, startOf(from), endOf(to),
                Limit.of(10))) {
            sellers.add(new SellerVolume(((Number) row[0]).longValue(), (String) row[1], ((Number) row[2]).longValue(),
                    money(row[3]), money(row[4]), money(row[5])));
        }
        return new PlatformSummary(from.toString(), to.toString(), "AED", t.payments, t.gross, t.collected,
                t.awaiting, t.fees, t.net, t.refunded, t.refunds, t.failed, t.successRate(),
                paymentService.feeRatePercent(), t.methods(), daily(null, from, to), sellers);
    }

    /** One point per day of the period (days without payments are zero, so charts have no gaps). */
    private List<DailyVolume> daily(Long sellerId, LocalDate from, LocalDate to) {
        Map<LocalDate, DailyVolume> days = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.put(day, new DailyVolume(day.toString(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        for (Object[] row : paymentRepository.daily(sellerId, PaymentService.SETTLED, startOf(from), endOf(to))) {
            LocalDate day = LocalDate.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            days.put(day, new DailyVolume(day.toString(), ((Number) row[3]).longValue(), money(row[4]), money(row[5])));
        }
        return new ArrayList<>(days.values());
    }

    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static Instant endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        if (from.plusYears(2).isBefore(to)) {
            throw ApiException.badRequest("The period can be at most 2 years");
        }
    }

    private static PageRequest page(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }

    static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
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
                    case CANCELLED -> {
                        // cash on delivery voided before collection - no money moved
                    }
                }
            }
            return t;
        }

        BigDecimal successRate() {
            long attempts = payments + refunds + failed;
            return attempts == 0 ? BigDecimal.ZERO : BigDecimal.valueOf((payments + refunds) * 100)
                    .divide(BigDecimal.valueOf(attempts), 1, RoundingMode.HALF_UP);
        }

        List<MethodTotal> methods() {
            List<MethodTotal> list = new ArrayList<>(byMethod.values());
            list.sort((a, b) -> b.amount().compareTo(a.amount()));
            return list;
        }
    }
}
