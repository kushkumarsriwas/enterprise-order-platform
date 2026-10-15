package com.enterprise.inventory.stock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Ships from the default warehouse first, then from the warehouses holding the most units (fewest parcels). */
@Component
public class DefaultWarehouseFirstAllocation implements WarehouseAllocationStrategy {

    @Override
    public List<Allocation> allocate(List<WarehouseStock> stocks, int quantity) {
        List<WarehouseStock> ordered = new ArrayList<>(stocks);
        ordered.sort(Comparator
                .comparing((WarehouseStock s) -> !s.getWarehouse().isDefaultWarehouse())
                .thenComparing(WarehouseStock::getOnHand, Comparator.reverseOrder())
                .thenComparing(s -> s.getWarehouse().getId()));
        List<Allocation> plan = new ArrayList<>();
        int remaining = quantity;
        for (WarehouseStock stock : ordered) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, stock.getOnHand());
            if (take > 0) {
                plan.add(new Allocation(stock, take));
                remaining -= take;
            }
        }
        if (remaining > 0) {
            throw new IllegalStateException("Stock ledger out of balance: " + remaining + " unit(s) could not be allocated");
        }
        return plan;
    }
}
