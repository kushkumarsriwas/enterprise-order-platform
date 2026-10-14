package com.enterprise.inventory.stock;

import com.enterprise.inventory.stock.StockDtos.ReservationResponse;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API used by the order service. Not routed by the API gateway. */
@RestController
@RequestMapping("/internal/inventory/reservations")
@Tag(name = "Internal - stock reservations")
public class ReservationController {

    private final InventoryService inventoryService;

    public ReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @Operation(summary = "Reserve stock for an order (all-or-nothing, idempotent)")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/{orderRef}/commit")
    @Operation(summary = "Mark reserved stock as sold (order paid)")
    public ReservationResponse commit(@PathVariable String orderRef) {
        return inventoryService.commit(orderRef);
    }

    @PostMapping("/{orderRef}/release")
    @Operation(summary = "Return stock to available (order cancelled)")
    public ReservationResponse release(@PathVariable String orderRef) {
        return inventoryService.release(orderRef);
    }
}
