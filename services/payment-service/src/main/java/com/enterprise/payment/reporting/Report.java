package com.enterprise.payment.reporting;

import java.util.List;

/**
 * A printable business report: title, key figures and tables. Rendered to Excel ({@link ExcelReportWriter})
 * or PDF ({@link PdfReportWriter}) from the same data.
 */
public record Report(String title, String subtitle, List<Metric> summary, List<Table> tables) {

    public record Metric(String label, Object value) {
    }

    /** Cell values may be String, Number (BigDecimal = money) or null. */
    public record Table(String title, List<String> headers, List<List<Object>> rows) {
    }
}
