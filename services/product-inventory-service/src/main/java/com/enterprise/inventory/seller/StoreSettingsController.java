package com.enterprise.inventory.seller;

import com.enterprise.inventory.security.AuthenticatedUser;
import com.enterprise.inventory.seller.StoreSettingsService.StoreSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/store")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - store settings")
public class StoreSettingsController {

    private final StoreSettingsService service;

    public StoreSettingsController(StoreSettingsService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public StoreSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser seller) {
        return service.get(seller.id());
    }

    @PutMapping("/settings")
    @Operation(summary = "Vacation mode (hides every listing while you are away) and low-stock alerts")
    public StoreSettingsResponse update(@Valid @RequestBody SettingsRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser seller) {
        return service.update(seller.id(), request.vacationMode(), request.vacationMessage(),
                request.lowStockAlerts() == null || request.lowStockAlerts());
    }

    public record SettingsRequest(@NotNull Boolean vacationMode, @Size(max = 255) String vacationMessage,
                                  Boolean lowStockAlerts) {
    }
}
