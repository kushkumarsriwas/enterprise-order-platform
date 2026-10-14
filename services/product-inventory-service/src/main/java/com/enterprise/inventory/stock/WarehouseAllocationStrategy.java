package com.enterprise.inventory.stock;

import java.util.List;

/**
 * Strategy pattern: decides which warehouses ship the units of a paid order. Other strategies (nearest
 * warehouse to the customer, first-expiry-first-out) could be plugged in without touching the ledger.
 */
public interface WarehouseAllocationStrategy {

    List<Allocation> allocate(List<WarehouseStock> stocks, int quantity);

    record Allocation(WarehouseStock stock, int quantity) {
    }
}
