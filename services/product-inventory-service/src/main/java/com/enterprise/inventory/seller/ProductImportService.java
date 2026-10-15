package com.enterprise.inventory.seller;

import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.catalog.CategoryRepository;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.catalog.Unit;
import com.enterprise.inventory.common.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk upload of products from Excel. Every row is validated first; if any row has an error nothing is saved
 * (all-or-nothing), and the seller gets the list of errors with row numbers. Existing SKUs are updated.
 */
@Service
public class ProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);
    static final String[] COLUMNS = {"SKU*", "Name*", "Category", "Brand", "Barcode", "Unit", "Price*", "Was price",
            "Cost price*", "Reorder point", "Reorder quantity", "Listed (yes/no)", "Opening stock", "Description"};
    private static final int MAX_ROWS = 1000;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SellerProductService productService;

    public ProductImportService(ProductRepository productRepository, CategoryRepository categoryRepository,
                                SellerProductService productService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productService = productService;
    }

    public record RowError(int row, String message) {
    }

    public record ImportResult(boolean dryRun, int rows, int created, int updated, List<RowError> errors) {
    }

    private record ParsedRow(int row, String sku, String name, Long categoryId, String brand, String barcode, Unit unit,
                             BigDecimal price, BigDecimal compareAt, BigDecimal cost, Integer reorderPoint,
                             Integer reorderQuantity, boolean listed, Integer openingStock, String description) {
    }

    @Transactional
    public ImportResult importProducts(Long sellerId, String sellerName, String actor, MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose the Excel file to upload");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw ApiException.badRequest("The file must be 2 MB or smaller");
        }
        List<ParsedRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Map<String, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Function.identity()));
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.US);
            Set<String> skus = new HashSet<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || blankRow(row, formatter)) {
                    continue;
                }
                if (rows.size() + errors.size() >= MAX_ROWS) {
                    errors.add(new RowError(i + 1, "Only " + MAX_ROWS + " rows can be imported at once"));
                    break;
                }
                try {
                    ParsedRow parsed = parse(i + 1, row, formatter, categories);
                    if (!skus.add(parsed.sku())) {
                        throw new IllegalArgumentException("SKU " + parsed.sku() + " appears twice in the file");
                    }
                    rows.add(parsed);
                } catch (IllegalArgumentException ex) {
                    errors.add(new RowError(i + 1, ex.getMessage()));
                }
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ApiException apiException) {
                throw apiException;
            }
            throw ApiException.badRequest("The file is not a valid Excel workbook (.xlsx)");
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            throw ApiException.badRequest("The file has no product rows (start on row 2, below the headers)");
        }
        int created = 0;
        int updated = 0;
        for (ParsedRow row : rows) {
            boolean exists = productRepository.findBySellerIdAndSkuIgnoreCase(sellerId, row.sku())
                    .filter(Product::isActive).isPresent();
            if (exists) {
                updated++;
            } else {
                created++;
            }
        }
        if (!errors.isEmpty() || dryRun) {
            return new ImportResult(dryRun || !errors.isEmpty(), rows.size() + errors.size(), errors.isEmpty() ? created : 0,
                    errors.isEmpty() ? updated : 0, errors);
        }
        for (ParsedRow row : rows) {
            try {
                Product existing = productRepository.findBySellerIdAndSkuIgnoreCase(sellerId, row.sku())
                        .filter(Product::isActive).orElse(null);
                if (existing == null) {
                    productService.create(sellerId, sellerName, actor, new CreateProductRequest(row.sku(), row.barcode(),
                            row.name(), row.brand(), row.description(), row.unit(), row.categoryId(), row.price(),
                            row.compareAt(), row.cost(), null, row.reorderPoint(), row.reorderQuantity(), null,
                            row.listed(), row.openingStock(), null));
                } else {
                    productService.update(sellerId, existing.getId(), new UpdateProductRequest(row.barcode(), row.name(),
                            row.brand(), row.description(), row.unit(), row.categoryId(), row.price(), row.compareAt(),
                            row.cost(), existing.getImageUrl(),
                            row.reorderPoint() == null ? existing.getReorderPoint() : row.reorderPoint(),
                            row.reorderQuantity() == null ? existing.getReorderQuantity() : row.reorderQuantity(),
                            existing.getPreferredSupplier() == null ? null : existing.getPreferredSupplier().getId(),
                            row.listed()));
                }
            } catch (ApiException ex) {
                // a business rule failed for this row: undo the whole import and report it
                throw ApiException.badRequest("Row " + row.row() + ": " + ex.getMessage());
            }
        }
        log.info("{} imported {} new and {} updated product(s) from Excel", actor, created, updated);
        return new ImportResult(false, rows.size(), created, updated, List.of());
    }

    /** Empty workbook with the expected columns and one example row. */
    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Products");
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row header = sheet.createRow(0);
            for (int c = 0; c < COLUMNS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(COLUMNS[c]);
                cell.setCellStyle(bold);
                sheet.setColumnWidth(c, 18 * 256);
            }
            Object[] example = {"EX-1001", "Example wireless mouse", "Electronics", "Nexo", "6291041500213", "PIECE",
                    79.00, 99.00, 45.00, 10, 50, "yes", 120, "Delete this example row before uploading"};
            Row row = sheet.createRow(1);
            for (int c = 0; c < example.length; c++) {
                if (example[c] instanceof Number number) {
                    row.createCell(c).setCellValue(number.doubleValue());
                } else {
                    row.createCell(c).setCellValue(example[c].toString());
                }
            }
            Sheet help = workbook.createSheet("Help");
            String[] lines = {"Columns marked * are required.",
                    "Category must match a marketplace department name exactly (e.g. Electronics).",
                    "Unit: PIECE, PACK, BOX, CARTON, PAIR, SET, KG, GRAM, LITRE or METRE (default PIECE).",
                    "Listed: yes = visible to customers, no = inventory only (listing needs a category).",
                    "Opening stock is only used for new SKUs and goes into your default warehouse.",
                    "Existing SKUs are updated; nothing is saved if any row has an error."};
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 100 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not build the import template", ex);
        }
    }

    private static ParsedRow parse(int rowNumber, Row row, DataFormatter f, Map<String, Category> categories) {
        String sku = text(row, 0, f);
        String name = text(row, 1, f);
        if (sku == null || !sku.matches("^[A-Za-z0-9][A-Za-z0-9-]{0,39}$")) {
            throw new IllegalArgumentException("SKU is required (letters, digits and '-', max 40)");
        }
        if (name == null || name.length() > 150) {
            throw new IllegalArgumentException("Name is required (max 150 characters)");
        }
        String categoryName = text(row, 2, f);
        Long categoryId = null;
        if (categoryName != null) {
            Category category = categories.get(categoryName.toLowerCase(Locale.ROOT));
            if (category == null) {
                throw new IllegalArgumentException("Unknown category '" + categoryName + "'");
            }
            categoryId = category.getId();
        }
        String barcode = text(row, 4, f);
        if (barcode != null && !barcode.matches("^\\d{8,14}$")) {
            throw new IllegalArgumentException("Barcode must be 8 to 14 digits");
        }
        Unit unit;
        try {
            String unitText = text(row, 5, f);
            unit = unitText == null ? Unit.PIECE : Unit.valueOf(unitText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown unit '" + text(row, 5, f) + "'");
        }
        BigDecimal price = money(row, 6, f, "Price", true);
        BigDecimal compareAt = money(row, 7, f, "Was price", false);
        BigDecimal cost = money(row, 8, f, "Cost price", true);
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }
        if (compareAt != null && compareAt.compareTo(price) <= 0) {
            throw new IllegalArgumentException("Was price must be higher than the price");
        }
        String listedText = text(row, 11, f);
        boolean listed = listedText != null && Set.of("yes", "y", "true", "1").contains(listedText.toLowerCase(Locale.ROOT));
        if (listed && categoryId == null) {
            throw new IllegalArgumentException("A listed product needs a category");
        }
        String description = text(row, 13, f);
        return new ParsedRow(rowNumber, sku.toUpperCase(Locale.ROOT), name, categoryId, text(row, 3, f), barcode, unit,
                price, compareAt, cost, whole(row, 9, f, "Reorder point"), whole(row, 10, f, "Reorder quantity"), listed,
                whole(row, 12, f, "Opening stock"),
                description != null && description.length() > 2000 ? description.substring(0, 2000) : description);
    }

    private static boolean blankRow(Row row, DataFormatter f) {
        for (int c = 0; c < COLUMNS.length; c++) {
            if (text(row, c, f) != null) {
                return false;
            }
        }
        return true;
    }

    private static String text(Row row, int column, DataFormatter f) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        String value = f.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal money(Row row, int column, DataFormatter f, String label, boolean required) {
        String value = text(row, column, f);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(label + " is required");
            }
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "")).setScale(2, java.math.RoundingMode.HALF_UP);
            if (amount.signum() < 0 || amount.compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException(label + " must be between 0 and 1,000,000");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " '" + value + "' is not a number");
        }
    }

    private static Integer whole(Row row, int column, DataFormatter f, String label) {
        String value = text(row, column, f);
        if (value == null) {
            return null;
        }
        try {
            int number = new BigDecimal(value.replace(",", "")).intValueExact();
            if (number < 0 || number > 1_000_000) {
                throw new IllegalArgumentException(label + " must be between 0 and 1,000,000");
            }
            return number;
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException(label + " '" + value + "' is not a whole number");
        }
    }
}
