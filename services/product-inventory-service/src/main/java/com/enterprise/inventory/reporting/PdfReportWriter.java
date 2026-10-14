package com.enterprise.inventory.reporting;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small dependency-free PDF renderer for {@link Report}s (A4, standard Helvetica fonts, KPI tiles and
 * paginated tables with repeated headers). Writes the PDF objects, content streams and xref table directly.
 */
public final class PdfReportWriter {

    public static final String CONTENT_TYPE = "application/pdf";

    private static final float A4_SHORT = 595f;
    private static final float A4_LONG = 842f;
    private static final float MARGIN = 40f;
    private static final float ROW_H = 16f;

    /** Glyph widths (1/1000 em) for ASCII 32..126, from the standard Helvetica / Helvetica-Bold metrics. */
    private static final int[] REGULAR = {278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278,
            278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, 1015, 667, 667, 722,
            722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667,
            667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833,
            556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};
    private static final int[] BOLD = {278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278,
            278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611, 975, 722, 722, 722,
            722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667,
            667, 611, 333, 278, 333, 584, 556, 333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889,
            611, 611, 611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584};

    private PdfReportWriter() {
    }

    public static byte[] write(Report report) {
        // Wide tables switch the whole document to landscape so columns don't get truncated.
        float widest = 0;
        for (Report.Table table : report.tables()) {
            float sum = 0;
            for (float w : naturalWidths(table)) {
                sum += w;
            }
            widest = Math.max(widest, sum);
        }
        boolean landscape = widest > A4_SHORT - 2 * MARGIN;
        Layout layout = new Layout(landscape ? A4_LONG : A4_SHORT, landscape ? A4_SHORT : A4_LONG);
        layout.heading(report.title(), report.subtitle());
        layout.metrics(report.summary());
        for (Report.Table table : report.tables()) {
            layout.table(table);
        }
        return layout.render();
    }

    static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
            return String.format(Locale.US, "%,.2f", ((Number) value).doubleValue());
        }
        if (value instanceof Number number) {
            return String.format(Locale.US, "%,d", number.longValue());
        }
        return value.toString();
    }

    /** Width each column needs: header or widest value; numbers are never truncated. */
    static float[] naturalWidths(Report.Table table) {
        int columns = table.headers().size();
        float[] widths = new float[columns];
        for (int c = 0; c < columns; c++) {
            widths[c] = width(table.headers().get(c), true, 9) + 12;
        }
        for (List<Object> row : table.rows()) {
            for (int c = 0; c < columns && c < row.size(); c++) {
                Object value = row.get(c);
                float w = width(format(value), false, 9) + 12;
                widths[c] = Math.max(widths[c], value instanceof Number ? w : Math.min(220, w));
            }
        }
        return widths;
    }

    private static float width(String text, boolean bold, float size) {
        int[] widths = bold ? BOLD : REGULAR;
        float total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            total += c >= 32 && c <= 126 ? widths[c - 32] : 556;
        }
        return total * size / 1000f;
    }

    private static String fit(String text, boolean bold, float size, float maxWidth) {
        if (width(text, bold, size) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && width(cut + "...", bold, size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == ')' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 32 || c > 255) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final class Layout {

        private final float pageW;
        private final float pageH;
        private final float contentW;
        private final List<StringBuilder> pages = new ArrayList<>();
        private StringBuilder page;
        private float y;

        Layout(float pageW, float pageH) {
            this.pageW = pageW;
            this.pageH = pageH;
            this.contentW = pageW - 2 * MARGIN;
            newPage();
        }

        private void newPage() {
            page = new StringBuilder();
            pages.add(page);
            y = pageH - MARGIN;
        }

        private boolean fits(float height) {
            return y - height >= MARGIN + 24;
        }

        private void text(String content, boolean bold, float size, float x, float baseline, float gray) {
            page.append("BT /").append(bold ? "F2 " : "F1 ").append(size).append(" Tf ")
                    .append(gray).append(" g ").append(x).append(' ').append(baseline).append(" Td (")
                    .append(escape(content)).append(") Tj ET\n");
        }

        private void rect(float x, float bottom, float w, float h, float gray) {
            page.append(gray).append(" g ").append(x).append(' ').append(bottom).append(' ')
                    .append(w).append(' ').append(h).append(" re f\n");
        }

        private void stroke(float x, float bottom, float w, float h, float gray) {
            page.append(gray).append(" G 0.6 w ").append(x).append(' ').append(bottom).append(' ')
                    .append(w).append(' ').append(h).append(" re S\n");
        }

        void heading(String title, String subtitle) {
            rect(MARGIN, y - 4, contentW, 3, 0.15f);
            y -= 26;
            text(fit(title, true, 18, contentW), true, 18, MARGIN, y, 0.1f);
            y -= 16;
            if (subtitle != null && !subtitle.isBlank()) {
                text(fit(subtitle, false, 10, contentW), false, 10, MARGIN, y, 0.4f);
                y -= 14;
            }
            y -= 10;
        }

        void metrics(List<Report.Metric> metrics) {
            int perRow = 3;
            float gap = 8;
            float tileW = (contentW - gap * (perRow - 1)) / perRow;
            float tileH = 42;
            for (int i = 0; i < metrics.size(); i += perRow) {
                if (!fits(tileH + gap)) {
                    newPage();
                }
                for (int j = 0; j < perRow && i + j < metrics.size(); j++) {
                    Report.Metric metric = metrics.get(i + j);
                    float x = MARGIN + j * (tileW + gap);
                    rect(x, y - tileH, tileW, tileH, 0.96f);
                    stroke(x, y - tileH, tileW, tileH, 0.85f);
                    text(fit(metric.label(), false, 8, tileW - 16), false, 8, x + 8, y - 14, 0.4f);
                    text(fit(format(metric.value()), true, 13, tileW - 16), true, 13, x + 8, y - 32, 0.1f);
                }
                y -= tileH + gap;
            }
            y -= 8;
        }

        void table(Report.Table table) {
            int columns = table.headers().size();
            if (columns == 0) {
                return;
            }
            boolean[] numeric = new boolean[columns];
            for (List<Object> row : table.rows()) {
                for (int c = 0; c < columns && c < row.size(); c++) {
                    numeric[c] |= row.get(c) instanceof Number;
                }
            }
            float[] widths = naturalWidths(table);
            float total = 0;
            float textTotal = 0;
            for (int c = 0; c < columns; c++) {
                total += widths[c];
                textTotal += numeric[c] ? 0 : widths[c];
            }
            if (total > contentW && textTotal > 0 && total - textTotal < contentW) {
                // shrink only the text columns, keep numbers fully visible
                float scale = (contentW - (total - textTotal)) / textTotal;
                for (int c = 0; c < columns; c++) {
                    widths[c] = numeric[c] ? widths[c] : widths[c] * scale;
                }
            } else {
                for (int c = 0; c < columns; c++) {
                    widths[c] = widths[c] * contentW / total;
                }
            }

            if (!fits(24 + ROW_H * 3)) {
                newPage();
            }
            y -= 6;
            text(fit(table.title(), true, 11, contentW), true, 11, MARGIN, y, 0.1f);
            y -= 10;
            header(table, widths, numeric);
            if (table.rows().isEmpty()) {
                y -= ROW_H;
                text("No data for this period", false, 9, MARGIN + 6, y + 5, 0.45f);
            }
            int index = 0;
            for (List<Object> row : table.rows()) {
                if (!fits(ROW_H)) {
                    newPage();
                    header(table, widths, numeric);
                }
                y -= ROW_H;
                if (index++ % 2 == 1) {
                    rect(MARGIN, y, contentW, ROW_H, 0.975f);
                }
                float x = MARGIN;
                for (int c = 0; c < columns; c++) {
                    String value = fit(c < row.size() ? format(row.get(c)) : "", false, 9, widths[c] - 10);
                    float tx = numeric[c] ? x + widths[c] - 5 - width(value, false, 9) : x + 5;
                    text(value, false, 9, tx, y + 5, 0.15f);
                    x += widths[c];
                }
            }
            y -= 18;
        }

        private void header(Report.Table table, float[] widths, boolean[] numeric) {
            y -= ROW_H + 2;
            rect(MARGIN, y, contentW, ROW_H + 2, 0.9f);
            float x = MARGIN;
            for (int c = 0; c < widths.length; c++) {
                String label = fit(table.headers().get(c), true, 9, widths[c] - 10);
                float tx = numeric[c] ? x + widths[c] - 5 - width(label, true, 9) : x + 5;
                text(label, true, 9, tx, y + 6, 0.1f);
                x += widths[c];
            }
        }

        byte[] render() {
            String generated = "Generated " + ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'", Locale.US)) + "  -  Nexora Commerce";
            for (int i = 0; i < pages.size(); i++) {
                StringBuilder content = pages.get(i);
                page = content;
                String number = "Page " + (i + 1) + " of " + pages.size();
                text(generated, false, 8, MARGIN, 24, 0.5f);
                text(number, false, 8, pageW - MARGIN - width(number, false, 8), 24, 0.5f);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            write(out, "%PDF-1.4\n%âãÏÓ\n");
            int pageCount = pages.size();
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pageCount; i++) {
                kids.append(5 + i * 2).append(" 0 R ");
            }
            object(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
            object(out, offsets, "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
            object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
            object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
            for (int i = 0; i < pageCount; i++) {
                object(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) pageW + " " + (int) pageH
                        + "] /Resources << /Font << /F1 3 0 R"
                        + " /F2 4 0 R >> >> /Contents " + (6 + i * 2) + " 0 R >>");
                byte[] stream = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
                offsets.add(out.size());
                write(out, (6 + i * 2) + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
                out.writeBytes(stream);
                write(out, "\nendstream\nendobj\n");
            }
            int xref = out.size();
            StringBuilder table = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
            for (int offset : offsets) {
                table.append(String.format("%010d 00000 n \n", offset));
            }
            table.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                    .append(xref).append("\n%%EOF\n");
            write(out, table.toString());
            return out.toByteArray();
        }

        private static void object(ByteArrayOutputStream out, List<Integer> offsets, String body) {
            offsets.add(out.size());
            write(out, (offsets.size()) + " 0 obj\n" + body + "\nendobj\n");
        }

        private static void write(ByteArrayOutputStream out, String text) {
            out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}
