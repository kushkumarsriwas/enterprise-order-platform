package com.enterprise.inventory.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WarehouseDtos {

    private WarehouseDtos() {
    }

    public record WarehouseRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{0,19}$", message = "letters, digits and '-' (max 20)")
            String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 60) String city,
            @Size(max = 200) String address) {
    }

    public record WarehouseResponse(Long id, String code, String name, String city, String address,
                                    boolean defaultWarehouse, boolean active, long productLines, long units,
                                    BigDecimal stockValueAtCost, Instant createdAt) {
    }

    public record TransferLineRequest(@NotNull Long productId, @NotNull @Min(1) @Max(1000000) Integer quantity) {
    }

    public record TransferRequest(@NotNull Long fromWarehouseId, @NotNull Long toWarehouseId,
                                  @NotEmpty @Size(max = 100) List<@Valid TransferLineRequest> lines,
                                  @Size(max = 255) String note) {
    }

    public record TransferLineResponse(Long productId, String sku, String productName, int quantity) {
    }

    public record TransferResponse(Long id, String number, Long fromWarehouseId, String fromWarehouse,
                                   Long toWarehouseId, String toWarehouse, int totalUnits, String note,
                                   String createdBy, Instant createdAt, List<TransferLineResponse> lines) {
    }
}
