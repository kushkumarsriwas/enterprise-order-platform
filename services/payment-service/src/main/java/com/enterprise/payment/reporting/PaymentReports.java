package com.enterprise.payment.reporting;

import com.enterprise.payment.payment.PaymentDtos.AdminPaymentResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Excel/PDF exports of transaction lists (seller payments screen and administrator transaction explorer). */
public final class PaymentReports {

    private PaymentReports() {
    }

    public static Report transactions(String title, String subtitle, List<AdminPaymentResponse> payments,
                                      boolean platformView) {
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        long failed = 0;
        List<List<Object>> rows = new ArrayList<>();
        for (AdminPaymentResponse p : payments) {
            if ("SUCCESS".equals(p.status()) || "PENDING".equals(p.status())) {
                volume = volume.add(p.amount());
                fees = fees.add(p.platformFee());
                net = net.add(p.sellerNet());
            } else if ("FAILED".equals(p.status())) {
                failed++;
            }
            List<Object> row = new ArrayList<>(List.of(ReportFiles.dateTime(p.createdAt()), p.transactionReference(),
                    p.orderNumber(), ReportFiles.orDash(p.customerUsername())));
            if (platformView) {
                row.add(ReportFiles.orDash(p.sellerName()));
            }
            row.addAll(List.of(p.method(), ReportFiles.orDash(p.maskedInstrument()), p.status(), p.amount(),
                    p.platformFee(), p.sellerNet(),
                    ReportFiles.orDash(p.failureReason() != null ? p.failureReason() : p.refundReason())));
            rows.add(row);
        }
        List<String> headers = new ArrayList<>(List.of("Date (UTC)", "Transaction", "Order", "Customer"));
        if (platformView) {
            headers.add("Store");
        }
        headers.addAll(List.of("Method", "Instrument", "Status", "Amount", "Platform fee", "Seller net", "Note"));
        return new Report(title, subtitle,
                List.of(new Report.Metric("Transactions", payments.size()),
                        new Report.Metric("Volume (paid + COD)", volume),
                        new Report.Metric(platformView ? "Platform revenue" : "Platform fees", fees),
                        new Report.Metric(platformView ? "Due to sellers" : "Net earnings", net),
                        new Report.Metric("Failed attempts", failed)),
                List.of(new Report.Table("Transactions", headers, rows)));
    }
}
