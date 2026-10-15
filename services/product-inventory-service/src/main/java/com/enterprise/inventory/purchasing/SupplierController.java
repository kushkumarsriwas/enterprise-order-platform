package com.enterprise.inventory.purchasing;

import static com.enterprise.inventory.reporting.ReportFiles.orDash;

import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.SupplierRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.SupplierResponse;
import com.enterprise.inventory.reporting.Report;
import com.enterprise.inventory.reporting.ReportFiles;
import com.enterprise.inventory.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/suppliers")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Suppliers with orders, spend and on-time delivery (search, active filter, sort)")
    public PageResponse<SupplierResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "name") String sort,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.list(seller.id(), search, active, sort, page, size);
    }

    @GetMapping("/all")
    @Operation(summary = "Every supplier (for drop-down lists)")
    public List<SupplierResponse> all(@AuthenticationPrincipal AuthenticatedUser seller) {
        return service.all(seller.id());
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.get(seller.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.create(seller.id(), request);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.update(seller.id(), id, request);
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Deactivate or reactivate a supplier")
    public SupplierResponse setActive(@PathVariable Long id, @RequestParam boolean active,
                                      @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.setActive(seller.id(), id, active);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Supplier list as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format, @AuthenticationPrincipal AuthenticatedUser seller) {
        List<SupplierResponse> suppliers = service.all(seller.id());
        Report report = new Report("Suppliers - " + seller.displayName(), suppliers.size() + " supplier(s), amounts in AED",
                List.of(new Report.Metric("Suppliers", suppliers.size()),
                        new Report.Metric("Open purchase orders",
                                suppliers.stream().mapToLong(SupplierResponse::openPurchaseOrders).sum())),
                List.of(new Report.Table("Suppliers", List.of("Supplier", "Contact", "E-mail", "Phone", "City",
                        "Lead time (days)", "Terms (days)", "POs", "Open", "Ordered", "On time %", "Status"),
                        suppliers.stream().map(s -> List.<Object>of(s.name(), orDash(s.contactName()), orDash(s.email()),
                                orDash(s.phone()), orDash(s.city()), s.leadTimeDays(), s.paymentTermsDays(),
                                s.purchaseOrders(), s.openPurchaseOrders(), s.orderedAmount(),
                                s.onTimeDeliveryPercent() == null ? "-" : s.onTimeDeliveryPercent() + "%",
                                s.active() ? "Active" : "Inactive")).toList())));
        return ReportFiles.download(report, format, "suppliers-" + LocalDate.now());
    }
}
