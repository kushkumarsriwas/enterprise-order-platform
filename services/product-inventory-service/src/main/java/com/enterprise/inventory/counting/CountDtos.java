package com.enterprise.inventory.counting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CountDtos {

    private CountDtos() {
    }

    public record StartCountRequest(Long warehouseId, Long categoryId, @Size(max = 255) String note) {
    }

    public record CountedLine(@NotNull Long lineId, @Min(0) @Max(1000000) Integer countedQuantity) {
    }

    public record RecordCountsRequest(@NotEmpty @Size(max = 2000) List<@Valid CountedLine> lines) {
    }

    public record CountLineResponse(Long id, Long productId, String sku, String productName, String binLocation,
                                    int expectedQuantity, Integer countedQuantity, int variance, BigDecimal unitCost,
                                    BigDecimal varianceValue) {
    }

    public record CountResponse(Long id, String number, Long warehouseId, String warehouseCode, String warehouseName,
                                Long categoryId, String categoryName, String status, String note, int lines,
                                int countedLines, int linesWithDifference, int varianceUnits, BigDecimal varianceValue,
                                String createdBy, Instant createdAt, String completedBy, Instant completedAt,
                                List<CountLineResponse> items) {
    }
}
