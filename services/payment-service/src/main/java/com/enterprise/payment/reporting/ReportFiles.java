package com.enterprise.payment.reporting;

import com.enterprise.payment.common.ApiException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Renders a {@link Report} as an Excel or PDF download (same data, two formats). */
public final class ReportFiles {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private ReportFiles() {
    }

    public static ResponseEntity<byte[]> download(Report report, String format, String baseName) {
        boolean excel = "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
        if (!excel && !"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("Format must be xlsx or pdf");
        }
        byte[] body = excel ? ExcelReportWriter.write(report) : PdfReportWriter.write(report);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(excel ? ExcelReportWriter.CONTENT_TYPE : PdfReportWriter.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(baseName + (excel ? ".xlsx" : ".pdf")).build().toString())
                .body(body);
    }

    /** A ready-made PDF document (invoice, packing slip) as a download. */
    public static ResponseEntity<byte[]> pdf(String fileName, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(content);
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant);
    }

    public static String date(Instant instant) {
        return instant == null ? "-" : instant.toString().substring(0, 10);
    }

    public static Object orDash(Object value) {
        return value == null || (value instanceof String s && s.isBlank()) ? "-" : value;
    }
}
