package com.enterprise.inventory.seller;

import com.enterprise.inventory.catalog.CatalogDtos.BulkRequest;
import com.enterprise.inventory.catalog.CatalogDtos.BulkResult;
import com.enterprise.inventory.catalog.CatalogDtos.CreateProductRequest;
import com.enterprise.inventory.catalog.CatalogDtos.ListingRequest;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductDetail;
import com.enterprise.inventory.catalog.CatalogDtos.SellerProductResponse;
import com.enterprise.inventory.catalog.CatalogDtos.UpdateProductRequest;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.catalog.SellerProductQuery;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.reporting.ExcelReportWriter;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.seller.ProductImportService.ImportResult;
import com.enterprise.inventory.stock.StockStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Seller Central - product catalogue (inventory items, listed or not). */
@RestController
@RequestMapping("/api/seller/products")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - products")
public class SellerProductController {

    private final SellerProductService service;
    private final ProductImportService importService;
    private final ProductRepository productRepository;

    public SellerProductController(SellerProductService service, ProductImportService importService,
                                   ProductRepository productRepository) {
        this.service = service;
        this.importService = importService;
        this.productRepository = productRepository;
    }

    @GetMapping
    @Operation(summary = "My products: text (name, SKU, barcode, brand), category, listing, stock status, supplier, "
            + "brand, warehouse, price range; sort by name, sku, newest, updated, price, available, value, best selling, margin")
    public PageResponse<SellerProductResponse> list(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) Long categoryId,
                                                    @RequestParam(required = false) Boolean listed,
                                                    @RequestParam(required = false) StockStatus stockStatus,
                                                    @RequestParam(required = false) Long supplierId,
                                                    @RequestParam(required = false) String brand,
                                                    @RequestParam(required = false) Long warehouseId,
                                                    @RequestParam(required = false) BigDecimal minPrice,
                                                    @RequestParam(required = false) BigDecimal maxPrice,
                                                    @RequestParam(defaultValue = "name") String sort,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "25") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.list(seller.id(), new SellerProductQuery.Filter(search, categoryId, listed, stockStatus,
                supplierId, brand, warehouseId, minPrice, maxPrice, sort), page, size);
    }

    @GetMapping("/brands")
    @Operation(summary = "Brands used in my catalogue (filter drop-down)")
    public List<String> brands(@AuthenticationPrincipal AuthenticatedUser seller) {
        return productRepository.brandsOfSeller(seller.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product with stock per warehouse, recent ledger rows, 30-day demand and incoming purchases")
    public SellerProductDetail get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.detail(seller.id(), id);
    }

    @PostMapping
    @Operation(summary = "Add a product to my inventory (listed on the marketplace only if 'listed' is true)")
    public ResponseEntity<SellerProductResponse> create(@Valid @RequestBody CreateProductRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        SellerProductResponse created = service.create(seller.id(), seller.displayName(), seller.username(), request);
        return ResponseEntity.created(URI.create("/api/seller/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public SellerProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.update(seller.id(), id, request);
    }

    @PatchMapping("/{id}/listing")
    @Operation(summary = "List the product for sale, or keep it as inventory only")
    public SellerProductResponse listing(@PathVariable Long id, @Valid @RequestBody ListingRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.setListing(seller.id(), id, request.listed());
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk action: list, unlist, archive, set category, change prices by %, set reorder point")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.bulk(seller.id(), request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a product (kept for order history and the stock ledger)")
    public ResponseEntity<Void> archive(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        service.archive(seller.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the product photo (JPEG, PNG or WebP, max 2 MB)")
    public SellerProductResponse uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                             @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.uploadImage(seller.id(), id, file);
    }

    @DeleteMapping("/{id}/image")
    public SellerProductResponse deleteImage(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.deleteImage(seller.id(), id);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk upload from Excel: validates every row first (dryRun=true only checks), all-or-nothing")
    public ImportResult importProducts(@RequestPart("file") MultipartFile file,
                                       @RequestParam(defaultValue = "false") boolean dryRun,
                                       @AuthenticationPrincipal AuthenticatedUser seller) {
        return importService.importProducts(seller.id(), seller.displayName(), seller.username(), file, dryRun);
    }

    @GetMapping("/import/template")
    @Operation(summary = "Excel template for the bulk upload")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExcelReportWriter.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("nexora-product-upload-template.xlsx").build().toString())
                .body(importService.template());
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "My products (same filters) as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Boolean listed,
                                         @RequestParam(required = false) StockStatus stockStatus,
                                         @RequestParam(required = false) Long supplierId,
                                         @RequestParam(required = false) String brand,
                                         @RequestParam(required = false) Long warehouseId,
                                         @RequestParam(required = false) BigDecimal minPrice,
                                         @RequestParam(required = false) BigDecimal maxPrice,
                                         @RequestParam(defaultValue = "name") String sort,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        List<SellerProductResponse> products = service.export(seller.id(), new SellerProductQuery.Filter(search,
                categoryId, listed, stockStatus, supplierId, brand, warehouseId, minPrice, maxPrice, sort));
        Report report = new Report("Products - " + seller.displayName(), products.size() + " product(s) on "
                + LocalDate.now() + ", amounts in AED",
                List.of(new Report.Metric("Products", products.size()),
                        new Report.Metric("Listed", products.stream().filter(SellerProductResponse::listed).count()),
                        new Report.Metric("Inventory only", products.stream().filter(p -> !p.listed()).count()),
                        new Report.Metric("Stock value at cost", products.stream().map(SellerProductResponse::stockValueAtCost)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))),
                List.of(new Report.Table("Products", List.of("SKU", "Product", "Brand", "Category", "Listed", "Price",
                        "Cost", "Margin %", "Available", "Reserved", "Incoming", "Status", "Supplier", "Units sold"),
                        products.stream().map(p -> List.<Object>of(p.sku(), p.name(), ReportFiles.orDash(p.brand()),
                                ReportFiles.orDash(p.categoryName()), p.listed() ? "Yes" : "No", p.price(), p.costPrice(),
                                p.marginPercent(), p.availableQuantity(), p.reservedQuantity(), p.incomingQuantity(),
                                p.stockStatus().replace('_', ' '), ReportFiles.orDash(p.preferredSupplierName()),
                                p.unitsSold())).toList())));
        return ReportFiles.download(report, format, "products-" + LocalDate.now());
    }
}
