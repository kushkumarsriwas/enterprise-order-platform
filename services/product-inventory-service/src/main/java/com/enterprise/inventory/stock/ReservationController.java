package com.enterprise.inventory.stock;

import com.enterprise.inventory.stock.StockDtos.LookupRequest;
import com.enterprise.inventory.stock.StockDtos.ProductLookup;
import com.enterprise.inventory.stock.StockDtos.ReservationResponse;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API used by the order service. Not routed by the API gateway. */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal - stock reservations")
public class ReservationController {

    private final InventoryService inventoryService;

    public ReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/products/lookup")
    @Operation(summary = "Seller, price and availability for a set of products")
    public List<ProductLookup> lookup(@Valid @RequestBody LookupRequest request) {
        return inventoryService.lookup(request.productIds());
    }

    @PostMapping("/inventory/reservations")
    @Operation(summary = "Reserve stock for one seller's order (all-or-nothing, idempotent)")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/inventory/reservations/{orderRef}/commit")
    @Operation(summary = "Mark reserved stock as sold (order paid)")
    public ReservationResponse commit(@PathVariable String orderRef) {
        return inventoryService.commit(orderRef);
    }

    @PostMapping("/inventory/reservations/{orderRef}/release")
    @Operation(summary = "Return stock to available (order cancelled)")
    public ReservationResponse release(@PathVariable String orderRef) {
        return inventoryService.release(orderRef);
    }
}
