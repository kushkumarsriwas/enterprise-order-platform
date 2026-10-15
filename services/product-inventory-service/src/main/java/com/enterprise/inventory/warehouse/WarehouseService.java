package com.enterprise.inventory.warehouse;

import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.stock.WarehouseStockRepository;
import com.enterprise.inventory.warehouse.WarehouseDtos.WarehouseRequest;
import com.enterprise.inventory.warehouse.WarehouseDtos.WarehouseResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A seller's stock locations. Every seller has exactly one default warehouse (created on first use). */
@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final WarehouseRepository repository;
    private final WarehouseStockRepository stockRepository;

    public WarehouseService(WarehouseRepository repository, WarehouseStockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    /** The default warehouse; a new seller gets "MAIN - Main warehouse" automatically. */
    @Transactional
    public Warehouse defaultFor(Long sellerId) {
        return repository.findFirstBySellerIdAndDefaultWarehouseTrue(sellerId)
                .orElseGet(() -> {
                    log.info("Creating the default warehouse of seller {}", sellerId);
                    return repository.save(new Warehouse(sellerId, "MAIN", "Main warehouse", null, null, true));
                });
    }

    /** The given warehouse (must belong to the seller and be active) or the default one when id is null. */
    @Transactional
    public Warehouse resolve(Long sellerId, Long warehouseId) {
        if (warehouseId == null) {
            return defaultFor(sellerId);
        }
        Warehouse warehouse = repository.findByIdAndSellerId(warehouseId, sellerId)
                .orElseThrow(() -> ApiException.notFound("Warehouse " + warehouseId + " not found"));
        if (!warehouse.isActive()) {
            throw ApiException.conflict("Warehouse " + warehouse.getCode() + " is closed");
        }
        return warehouse;
    }

    @Transactional
    public List<WarehouseResponse> list(Long sellerId) {
        defaultFor(sellerId);
        return listExisting(sellerId);
    }

    /** Read-only variant (the administrator looking at a store must not create anything). */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listExisting(Long sellerId) {
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] row : stockRepository.totalsByWarehouse(sellerId)) {
            totals.put((Long) row[0], row);
        }
        return repository.findBySellerIdOrderByDefaultWarehouseDescCodeAsc(sellerId).stream()
                .map(w -> toResponse(w, totals.get(w.getId()))).toList();
    }

    @Transactional
    public WarehouseResponse create(Long sellerId, WarehouseRequest request) {
        defaultFor(sellerId);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsBySellerIdAndCodeIgnoreCase(sellerId, code)) {
            throw ApiException.conflict("You already have a warehouse with code " + code);
        }
        if (repository.countBySellerId(sellerId) >= 25) {
            throw ApiException.badRequest("A store can have at most 25 warehouses");
        }
        Warehouse warehouse = repository.save(new Warehouse(sellerId, code, request.name().trim(), trim(request.city()),
                trim(request.address()), false));
        return toResponse(warehouse, null);
    }

    @Transactional
    public WarehouseResponse update(Long sellerId, Long id, WarehouseRequest request) {
        Warehouse warehouse = owned(sellerId, id);
        warehouse.update(request.name().trim(), trim(request.city()), trim(request.address()));
        return toResponse(warehouse, null);
    }

    /** Moves the "default" flag (receiving + shipping preference) to another active warehouse. */
    @Transactional
    public List<WarehouseResponse> makeDefault(Long sellerId, Long id) {
        Warehouse target = owned(sellerId, id);
        if (!target.isActive()) {
            throw ApiException.conflict("A closed warehouse can't be the default");
        }
        repository.findBySellerIdOrderByDefaultWarehouseDescCodeAsc(sellerId)
                .forEach(w -> w.setDefaultWarehouse(w.getId().equals(id)));
        return list(sellerId);
    }

    /** Close (or reopen) a warehouse. Only an empty, non-default warehouse can be closed. */
    @Transactional
    public WarehouseResponse setActive(Long sellerId, Long id, boolean active) {
        Warehouse warehouse = owned(sellerId, id);
        if (!active) {
            if (warehouse.isDefaultWarehouse()) {
                throw ApiException.conflict("Choose another default warehouse before closing " + warehouse.getCode());
            }
            long units = stockRepository.unitsIn(id);
            if (units > 0) {
                throw ApiException.conflict(warehouse.getCode() + " still holds " + units
                        + " unit(s). Transfer them to another warehouse first.");
            }
        }
        warehouse.setActive(active);
        return toResponse(warehouse, null);
    }

    private Warehouse owned(Long sellerId, Long id) {
        return repository.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> ApiException.notFound("Warehouse " + id + " not found"));
    }

    private static WarehouseResponse toResponse(Warehouse w, Object[] totals) {
        long lines = totals == null ? 0 : ((Number) totals[1]).longValue();
        long units = totals == null ? 0 : ((Number) totals[2]).longValue();
        BigDecimal value = totals == null ? BigDecimal.ZERO : new BigDecimal(totals[3].toString());
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getCity(), w.getAddress(),
                w.isDefaultWarehouse(), w.isActive(), lines, units, value.setScale(2, RoundingMode.HALF_UP),
                w.getCreatedAt());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
