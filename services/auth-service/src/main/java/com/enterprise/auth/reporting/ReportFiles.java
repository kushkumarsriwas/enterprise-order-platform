package com.enterprise.auth.reporting;

import com.enterprise.auth.common.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Renders a {@link Report} as an Excel or PDF download. */
public final class ReportFiles {

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
}
