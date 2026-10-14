package com.enterprise.inventory.counting;

import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.counting.CountDtos.CountResponse;
import com.enterprise.inventory.counting.CountDtos.RecordCountsRequest;
import com.enterprise.inventory.counting.CountDtos.StartCountRequest;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/stock-counts")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - stock counts")
public class StockCountController {

    private final StockCountService service;

    public StockCountController(StockCountService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CountResponse> list(@RequestParam(required = false) StockCountStatus status,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.list(seller.id(), status, warehouseId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a stock count of a warehouse (optionally one category): expected quantities are frozen")
    public CountResponse start(@Valid @RequestBody StartCountRequest request,
                               @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.start(seller.id(), seller.username(), request);
    }

    @GetMapping("/{id}")
    public CountResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.get(seller.id(), id);
    }

    @PutMapping("/{id}/lines")
    @Operation(summary = "Save counted quantities (can be done in several rounds)")
    public CountResponse record(@PathVariable Long id, @Valid @RequestBody RecordCountsRequest request,
                                @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.record(seller.id(), id, request);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Post the differences to the stock ledger and close the count")
    public CountResponse complete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.complete(seller.id(), id, seller.username());
    }

    @PostMapping("/{id}/cancel")
    public CountResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.cancel(seller.id(), id, seller.username());
    }

    @GetMapping("/{id}/sheet")
    @Operation(summary = "Printable count sheet (PDF, blind count: expected quantities are not shown)")
    public ResponseEntity<byte[]> sheet(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        CountResponse count = service.get(seller.id(), id);
        Report report = new Report("Count sheet " + count.number(), count.warehouseCode() + " - " + count.warehouseName()
                + (count.categoryName() == null ? "" : " | " + count.categoryName()) + " | started by " + count.createdBy(),
                List.of(new Report.Metric("Products", count.lines())),
                List.of(new Report.Table("Count", List.of("Bin", "SKU", "Product", "Counted"),
                        count.items().stream().map(l -> List.<Object>of(l.binLocation() == null ? "-" : l.binLocation(),
                                l.sku(), l.productName(), "")).toList())));
        return ReportFiles.download(report, "pdf", count.number() + "-sheet");
    }

    @GetMapping("/{id}/export/{format}")
    @Operation(summary = "Variance report of a count as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @PathVariable String format,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        CountResponse count = service.get(seller.id(), id);
        Report report = new Report("Stock count " + count.number() + " - variance report",
                count.warehouseCode() + " - " + count.warehouseName() + " | " + count.status(),
                List.of(new Report.Metric("Products", count.lines()), new Report.Metric("Counted", count.countedLines()),
                        new Report.Metric("With differences", count.linesWithDifference()),
                        new Report.Metric("Variance (units)", count.varianceUnits()),
                        new Report.Metric("Variance value (AED)", count.varianceValue())),
                List.of(new Report.Table("Lines", List.of("SKU", "Product", "Bin", "Expected", "Counted", "Difference",
                        "Unit cost", "Value"),
                        count.items().stream().map(l -> List.<Object>of(l.sku(), l.productName(),
                                l.binLocation() == null ? "-" : l.binLocation(), l.expectedQuantity(),
                                l.countedQuantity() == null ? "-" : l.countedQuantity(), l.variance(), l.unitCost(),
                                l.varianceValue())).toList())));
        return ReportFiles.download(report, format, count.number() + "-variance");
    }
}
