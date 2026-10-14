package com.enterprise.order.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Writes a {@link Report} as an .xlsx workbook with Apache POI: a Summary sheet plus one sheet per table. */
public final class ExcelReportWriter {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelReportWriter() {
    }

    public static byte[] write(Report report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet summary = workbook.createSheet("Summary");
            Row titleRow = summary.createRow(0);
            cell(titleRow, 0, report.title(), styles.title);
            cell(summary.createRow(1), 0, report.subtitle(), styles.muted);
            int rowIndex = 3;
            for (Report.Metric metric : report.summary()) {
                Row row = summary.createRow(rowIndex++);
                cell(row, 0, metric.label(), styles.bold);
                value(row, 1, metric.value(), styles);
            }
            summary.setColumnWidth(0, 34 * 256);
            summary.setColumnWidth(1, 22 * 256);

            for (Report.Table table : report.tables()) {
                String name = WorkbookUtil.createSafeSheetName(table.title());
                if (workbook.getSheet(name) != null) {
                    name = WorkbookUtil.createSafeSheetName(table.title() + " " + workbook.getNumberOfSheets());
                }
                Sheet sheet = workbook.createSheet(name);
                Row header = sheet.createRow(0);
                int[] widths = new int[table.headers().size()];
                for (int c = 0; c < table.headers().size(); c++) {
                    cell(header, c, table.headers().get(c), styles.header);
                    widths[c] = table.headers().get(c).length();
                }
                int r = 1;
                for (List<Object> values : table.rows()) {
                    Row row = sheet.createRow(r++);
                    for (int c = 0; c < values.size() && c < widths.length; c++) {
                        value(row, c, values.get(c), styles);
                        widths[c] = Math.max(widths[c], display(values.get(c)).length());
                    }
                }
                for (int c = 0; c < widths.length; c++) {
                    sheet.setColumnWidth(c, Math.min(60, widths[c] + 3) * 256);
                }
                sheet.createFreezePane(0, 1);
                if (!table.rows().isEmpty()) {
                    sheet.setAutoFilter(new CellRangeAddress(0, table.rows().size(), 0, widths.length - 1));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create the Excel report", ex);
        }
    }

    private static void value(Row row, int column, Object value, Styles styles) {
        Cell cell = row.createCell(column);
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            cell.setCellStyle(styles.money);
        } else if (value instanceof Double || value instanceof Float) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(styles.money);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.longValue());
            cell.setCellStyle(styles.integer);
        } else {
            cell.setCellValue(value == null ? "" : value.toString());
        }
    }

    private static void cell(Row row, int column, String text, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(text == null ? "" : text);
        cell.setCellStyle(style);
    }

    private static String display(Object value) {
        return value == null ? "" : value instanceof BigDecimal decimal ? String.format("%,.2f", decimal) : value.toString();
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle muted;
        final CellStyle bold;
        final CellStyle header;
        final CellStyle money;
        final CellStyle integer;

        Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            Font mutedFont = workbook.createFont();
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            muted = workbook.createCellStyle();
            muted.setFont(mutedFont);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            integer = workbook.createCellStyle();
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        }
    }
}
