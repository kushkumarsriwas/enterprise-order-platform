package com.enterprise.order.reporting;

import java.util.List;

/**
 * A printable business document: title, key figures, text blocks and tables. Rendered to Excel
 * ({@link ExcelReportWriter}) or PDF ({@link PdfReportWriter}) from the same data.
 */
public record Report(String title, String subtitle, List<Metric> summary, List<Table> tables, List<Block> blocks) {

    public Report(String title, String subtitle, List<Metric> summary, List<Table> tables) {
        this(title, subtitle, summary, tables, List.of());
    }

    public record Metric(String label, Object value) {
    }

    /** Cell values may be String, Number (BigDecimal = money) or null. */
    public record Table(String title, List<String> headers, List<List<Object>> rows) {
    }

    /** A titled block of text lines, printed side by side with the other blocks (e.g. "Sold by" / "Ship to"). */
    public record Block(String title, List<String> lines) {
    }
}
