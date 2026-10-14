package com.enterprise.inventory.warehouse;

import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferRequest;
import com.enterprise.inventory.warehouse.WarehouseDtos.TransferResponse;
import com.enterprise.inventory.warehouse.WarehouseDtos.WarehouseRequest;
import com.enterprise.inventory.warehouse.WarehouseDtos.WarehouseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - warehouses and transfers")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final TransferService transferService;

    public WarehouseController(WarehouseService warehouseService, TransferService transferService) {
        this.warehouseService = warehouseService;
        this.transferService = transferService;
    }

    @GetMapping("/api/seller/warehouses")
    @Operation(summary = "My warehouses with product lines, units and stock value in each")
    public List<WarehouseResponse> list(@AuthenticationPrincipal AuthenticatedUser seller) {
        return warehouseService.list(seller.id());
    }

    @PostMapping("/api/seller/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return warehouseService.create(seller.id(), request);
    }

    @PutMapping("/api/seller/warehouses/{id}")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return warehouseService.update(seller.id(), id, request);
    }

    @PatchMapping("/api/seller/warehouses/{id}/default")
    @Operation(summary = "Make this the default warehouse (receives stock, ships orders first)")
    public List<WarehouseResponse> makeDefault(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return warehouseService.makeDefault(seller.id(), id);
    }

    @PatchMapping("/api/seller/warehouses/{id}/active")
    @Operation(summary = "Close (only when empty) or reopen a warehouse")
    public WarehouseResponse setActive(@PathVariable Long id, @RequestParam boolean active,
                                       @AuthenticationPrincipal AuthenticatedUser seller) {
        return warehouseService.setActive(seller.id(), id, active);
    }

    @GetMapping("/api/seller/transfers")
    @Operation(summary = "Stock transfers, newest first (filter by warehouse and date)")
    public PageResponse<TransferResponse> transfers(@RequestParam(required = false) Long warehouseId,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return transferService.list(seller.id(), warehouseId, from, to, page, size);
    }

    @PostMapping("/api/seller/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Move stock between two warehouses (one document, all lines or nothing)")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser seller) {
        return transferService.transfer(seller.id(), seller.username(), request);
    }

    @GetMapping("/api/seller/transfers/{id}")
    public TransferResponse transfer(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return transferService.get(seller.id(), id);
    }
}
