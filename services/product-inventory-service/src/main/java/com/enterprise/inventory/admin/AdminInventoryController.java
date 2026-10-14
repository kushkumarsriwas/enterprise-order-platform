package com.enterprise.inventory.admin;

import com.enterprise.inventory.analytics.AnalyticsDtos.InventoryAnalytics;
import com.enterprise.inventory.analytics.InventoryAnalyticsService;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductDetail;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductResponse;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.catalog.SellerProductQuery;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.counting.CountDtos.CountResponse;
import com.enterprise.inventory.counting.StockCountService;
import com.enterprise.inventory.counting.StockCountStatus;
import com.enterprise.inventory.purchasing.GoodsReceiptService;
import com.enterprise.inventory.purchasing.PurchaseOrderService;
import com.enterprise.inventory.purchasing.PurchaseOrderStatus;
import com.enterprise.inventory.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.SupplierResponse;
import com.enterprise.inventory.purchasing.SupplierService;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.seller.SellerProductService;
import com.enterprise.inventory.stock.InventoryService;
import com.enterprise.inventory.stock.InventoryService.MovementFilter;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockDtos.InventorySummary;
import com.enterprise.inventory.stock.StockDtos.MovementResponse;
import com.enterprise.inventory.stock.StockDtos.StockLevelRow;
import com.enterprise.inventory.stock.StockStatus;
import com.enterprise.inventory.warehouse.TransferService;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferResponse;
import com.enterprise.inventory.warehouse.WarehouseDtos.WarehouseResponse;
import com.enterprise.inventory.warehouse.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The administrator's read-only view of every seller's catalogue and inventory: the same services the
 * sellers use, called with the seller id the administrator is looking at (or none = whole platform).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - catalogue and inventory")
public class AdminInventoryController {

    private final SellerProductService productService;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final TransferService transferService;
    private final SupplierService supplierService;
    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceiptService goodsReceiptService;
    private final StockCountService stockCountService;
    private final InventoryAnalyticsService analyticsService;
    private final com.enterprise.inventory.stock.InventoryRepository inventoryRepository;

    public AdminInventoryController(SellerProductService productService, ProductRepository productRepository,
                                    InventoryService inventoryService, WarehouseService warehouseService,
                                    TransferService transferService, SupplierService supplierService,
                                    PurchaseOrderService purchaseOrderService, GoodsReceiptService goodsReceiptService,
                                    StockCountService stockCountService, InventoryAnalyticsService analyticsService,
                                    com.enterprise.inventory.stock.InventoryRepository inventoryRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.warehouseService = warehouseService;
        this.transferService = transferService;
        this.supplierService = supplierService;
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceiptService = goodsReceiptService;
        this.stockCountService = stockCountService;
        this.analyticsService = analyticsService;
        this.inventoryRepository = inventoryRepository;
    }

    public record SellerInventoryTotals(Long sellerId, String storeName, long products, long listed, long unitsOnHand,
                                        BigDecimal stockValueAtCost, BigDecimal stockValueAtPrice, long lowStock,
                                        long outOfStock) {
    }

    public record PlatformInventory(long sellers, long products, long listed, long unitsOnHand,
                                    BigDecimal stockValueAtCost, BigDecimal stockValueAtPrice, long lowStock,
                                    long outOfStock, List<SellerInventoryTotals> bySeller) {
    }

    @GetMapping("/inventory/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "Platform inventory: totals and per seller (products, listed, units, value, low / out of stock)")
    public PlatformInventory platform() {
        List<SellerInventoryTotals> rows = inventoryRepository.platformTotalsBySeller().stream()
                .map(r -> new SellerInventoryTotals((Long) r[0], (String) r[1], n(r[2]), n(r[3]), n(r[4]), money(r[5]),
                        money(r[6]), n(r[7]), n(r[8]))).toList();
        return new PlatformInventory(rows.size(), rows.stream().mapToLong(SellerInventoryTotals::products).sum(),
                rows.stream().mapToLong(SellerInventoryTotals::listed).sum(),
                rows.stream().mapToLong(SellerInventoryTotals::unitsOnHand).sum(),
                rows.stream().map(SellerInventoryTotals::stockValueAtCost).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(SellerInventoryTotals::stockValueAtPrice).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().mapToLong(SellerInventoryTotals::lowStock).sum(),
                rows.stream().mapToLong(SellerInventoryTotals::outOfStock).sum(), rows);
    }

    @GetMapping("/catalog/products")
    @Operation(summary = "Every product on the platform: seller, text, category, listing, stock status, sort")
    public PageResponse<SellerProductResponse> products(@RequestParam(required = false) Long sellerId,
                                                        @RequestParam(required = false) String search,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) Boolean listed,
                                                        @RequestParam(required = false) StockStatus stockStatus,
                                                        @RequestParam(required = false) BigDecimal minPrice,
                                                        @RequestParam(required = false) BigDecimal maxPrice,
                                                        @RequestParam(defaultValue = "newest") String sort,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return productService.list(sellerId, new SellerProductQuery.Filter(search, categoryId, listed, stockStatus, null,
                null, null, minPrice, maxPrice, sort), page, size);
    }

    @GetMapping("/catalog/products/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Any product with stock per warehouse, ledger, demand and incoming purchases")
    public SellerProductDetail product(@PathVariable Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> ApiException.notFound("Product " + id + " not found"));
        return productService.detail(product.getSellerId(), id);
    }

    @GetMapping("/catalog/products/export/{format}")
    @Operation(summary = "Platform product list (same filters) as xlsx or pdf")
    public ResponseEntity<byte[]> exportProducts(@PathVariable String format,
                                                 @RequestParam(required = false) Long sellerId,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) Boolean listed,
                                                 @RequestParam(required = false) StockStatus stockStatus,
                                                 @RequestParam(defaultValue = "newest") String sort) {
        List<SellerProductResponse> products = productService.export(sellerId, new SellerProductQuery.Filter(search,
                categoryId, listed, stockStatus, null, null, null, null, null, sort));
        Report report = new Report("Platform products - Nexora", products.size() + " product(s), amounts in AED",
                List.of(new Report.Metric("Products", products.size()),
                        new Report.Metric("Listed", products.stream().filter(SellerProductResponse::listed).count())),
                List.of(new Report.Table("Products", List.of("Store", "SKU", "Product", "Category", "Listed", "Price",
                        "Cost", "Available", "Reserved", "Status", "Units sold", "Rating"),
                        products.stream().map(p -> List.<Object>of(p.sellerName(), p.sku(), p.name(),
                                ReportFiles.orDash(p.categoryName()), p.listed() ? "Yes" : "No", p.price(), p.costPrice(),
                                p.availableQuantity(), p.reservedQuantity(), p.stockStatus().replace('_', ' '),
                                p.unitsSold(), p.ratingAverage())).toList())));
        return ReportFiles.download(report, format, "platform-products-" + LocalDate.now());
    }

    @GetMapping("/inventory/movements")
    @Operation(summary = "Stock ledger of the whole platform or one seller")
    public PageResponse<MovementResponse> movements(@RequestParam(required = false) Long sellerId,
                                                    @RequestParam(required = false) Long productId,
                                                    @RequestParam(required = false) Set<MovementType> type,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size) {
        return inventoryService.movements(sellerId, new MovementFilter(productId, null, type, null, search, from, to,
                false), page, size);
    }

    // ------------------------------------------------------------------ one seller's inventory system

    @GetMapping("/inventory/sellers/{sellerId}/summary")
    public InventorySummary sellerSummary(@PathVariable Long sellerId) {
        return inventoryService.summary(sellerId);
    }

    @GetMapping("/inventory/sellers/{sellerId}/stock")
    public PageResponse<StockLevelRow> sellerStock(@PathVariable Long sellerId,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(required = false) StockStatus stockStatus,
                                                   @RequestParam(required = false) Long warehouseId,
                                                   @RequestParam(defaultValue = "name") String sort,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return inventoryService.stockLevels(sellerId, new SellerProductQuery.Filter(search, null, null, stockStatus, null,
                null, warehouseId, null, null, sort), page, size);
    }

    @GetMapping("/inventory/sellers/{sellerId}/warehouses")
    public List<WarehouseResponse> sellerWarehouses(@PathVariable Long sellerId) {
        return warehouseService.listExisting(sellerId);
    }

    @GetMapping("/inventory/sellers/{sellerId}/transfers")
    public PageResponse<TransferResponse> sellerTransfers(@PathVariable Long sellerId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return transferService.list(sellerId, null, null, null, page, size);
    }

    @GetMapping("/inventory/sellers/{sellerId}/suppliers")
    public List<SupplierResponse> sellerSuppliers(@PathVariable Long sellerId) {
        return supplierService.all(sellerId);
    }

    @GetMapping("/inventory/sellers/{sellerId}/purchase-orders")
    public PageResponse<PurchaseOrderResponse> sellerPurchaseOrders(@PathVariable Long sellerId,
                                                                    @RequestParam(required = false) PurchaseOrderStatus status,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return purchaseOrderService.list(sellerId, new PurchaseOrderService.Filter(status, null, null, null, null, null,
                null, "newest"), page, size);
    }

    @GetMapping("/inventory/sellers/{sellerId}/purchase-orders/{id}")
    public PurchaseOrderResponse sellerPurchaseOrder(@PathVariable Long sellerId, @PathVariable Long id) {
        return purchaseOrderService.get(sellerId, id);
    }

    @GetMapping("/inventory/sellers/{sellerId}/goods-receipts")
    public PageResponse<GoodsReceiptResponse> sellerReceipts(@PathVariable Long sellerId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return goodsReceiptService.list(sellerId, null, null, null, null, null, page, size);
    }

    @GetMapping("/inventory/sellers/{sellerId}/stock-counts")
    public PageResponse<CountResponse> sellerCounts(@PathVariable Long sellerId,
                                                    @RequestParam(required = false) StockCountStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return stockCountService.list(sellerId, status, null, page, size);
    }

    @GetMapping("/inventory/sellers/{sellerId}/analytics")
    public InventoryAnalytics sellerAnalytics(@PathVariable Long sellerId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        return analyticsService.analyse(sellerId, start, end, 90);
    }

    private static long n(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO.setScale(2)
                : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
