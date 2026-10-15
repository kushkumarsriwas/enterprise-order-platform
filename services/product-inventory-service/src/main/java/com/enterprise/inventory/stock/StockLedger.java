package com.enterprise.inventory.stock;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.stock.WarehouseAllocationStrategy.Allocation;
import com.enterprise.inventory.warehouse.Warehouse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only class that changes physical stock. Every method runs inside the caller's transaction, after the
 * caller has locked the product's {@link Inventory} row with {@link #lock(Long)}; because of that lock the
 * per-warehouse rows can be read, created and changed without races. Each change keeps the invariant
 * {@code available + reserved == sum(on hand in all warehouses)} and writes one ledger row.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class StockLedger {

    private final InventoryRepository inventoryRepository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseAllocationStrategy allocationStrategy;

    public StockLedger(InventoryRepository inventoryRepository, WarehouseStockRepository stockRepository,
                       StockMovementRepository movementRepository, ProductRepository productRepository,
                       WarehouseAllocationStrategy allocationStrategy) {
        this.inventoryRepository = inventoryRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.allocationStrategy = allocationStrategy;
    }

    /** SELECT ... FOR UPDATE on the product's inventory row. Lock several products in ascending id order. */
    public Inventory lock(Long productId) {
        return inventoryRepository.findForUpdate(productId)
                .orElseThrow(() -> ApiException.notFound("No inventory record for product " + productId));
    }

    /** Units enter a warehouse. Purchases and opening stock update the weighted average cost. */
    public StockMovement stockIn(Inventory inventory, Warehouse warehouse, MovementType type, int quantity,
                                 BigDecimal unitCost, String reference, String note, String actor) {
        requirePositive(quantity);
        Product product = inventory.getProduct();
        if (type == MovementType.OPENING_STOCK || type == MovementType.RECEIVED) {
            product.receiveStockAtCost(inventory.onHand(), quantity, unitCost);
        }
        WarehouseStock stock = locate(product, warehouse);
        stock.add(quantity);
        inventory.addAvailable(quantity);
        return movementRepository.save(new StockMovement(product, warehouse, type, quantity, stock.getOnHand(),
                unitCost == null ? product.getCostPrice() : unitCost, null, null, reference, note, actor));
    }

    /** Manual correction or stock-count difference in one warehouse; reserved units can't be adjusted away. */
    public StockMovement adjust(Inventory inventory, Warehouse warehouse, MovementType type, int delta,
                                AdjustmentReason reason, String reference, String note, String actor) {
        if (delta == 0) {
            throw ApiException.badRequest("The adjustment quantity must not be zero");
        }
        Product product = inventory.getProduct();
        WarehouseStock stock = locate(product, warehouse);
        if (stock.getOnHand() + delta < 0) {
            throw ApiException.conflict("Only " + stock.getOnHand() + " unit(s) of " + product.getSku() + " are in "
                    + warehouse.label());
        }
        inventory.addAvailable(delta);
        stock.add(delta);
        return movementRepository.save(new StockMovement(product, warehouse, type, delta, stock.getOnHand(),
                product.getCostPrice(), null, reason, reference, note, actor));
    }

    /** Moves units between two warehouses: the product's total does not change. */
    public void transfer(Inventory inventory, Warehouse from, Warehouse to, int quantity, String reference,
                         String note, String actor) {
        requirePositive(quantity);
        Product product = inventory.getProduct();
        WarehouseStock source = locate(product, from);
        if (source.getOnHand() < quantity) {
            throw ApiException.conflict("Only " + source.getOnHand() + " unit(s) of " + product.getSku() + " are in "
                    + from.label());
        }
        WarehouseStock target = locate(product, to);
        source.add(-quantity);
        target.add(quantity);
        movementRepository.save(new StockMovement(product, from, MovementType.TRANSFER_OUT, -quantity,
                source.getOnHand(), product.getCostPrice(), null, null, reference, note, actor));
        movementRepository.save(new StockMovement(product, to, MovementType.TRANSFER_IN, quantity,
                target.getOnHand(), product.getCostPrice(), null, null, reference, note, actor));
    }

    /** A paid order: its reserved units leave the shelves, from the warehouses chosen by the allocation strategy. */
    public List<StockMovement> ship(Inventory inventory, int quantity, BigDecimal unitPrice, BigDecimal unitCost,
                                    String orderRef) {
        requirePositive(quantity);
        Product product = inventory.getProduct();
        inventory.consumeReserved(quantity);
        List<StockMovement> movements = new ArrayList<>();
        for (Allocation allocation : allocationStrategy.allocate(stockRepository.findByProduct(product.getId()), quantity)) {
            WarehouseStock stock = allocation.stock();
            stock.add(-allocation.quantity());
            movements.add(movementRepository.save(new StockMovement(product, stock.getWarehouse(), MovementType.SOLD,
                    -allocation.quantity(), stock.getOnHand(), unitCost, unitPrice, null, orderRef,
                    "Order paid - shipped from " + stock.getWarehouse().getCode(), "system")));
        }
        productRepository.addUnitsSold(product.getId(), quantity);
        return movements;
    }

    /** A paid order was cancelled: the units go back to the warehouses they were shipped from. */
    public void returnShipped(Inventory inventory, String orderRef, String actor) {
        Product product = inventory.getProduct();
        int returned = 0;
        for (StockMovement sold : movementRepository.findByProductReferenceAndType(product.getId(), orderRef,
                MovementType.SOLD)) {
            int units = -sold.getQuantity();
            WarehouseStock stock = locate(product, sold.getWarehouse());
            stock.add(units);
            inventory.addAvailable(units);
            movementRepository.save(new StockMovement(product, sold.getWarehouse(), MovementType.RETURNED, units,
                    stock.getOnHand(), sold.getUnitCost(), sold.getUnitPrice(), null, orderRef,
                    "Paid order cancelled - units back on the shelf", actor));
            returned += units;
        }
        if (returned > 0) {
            productRepository.addUnitsSold(product.getId(), -returned);
        }
    }

    /** Reservation events are recorded too, so the ledger explains every change of the available quantity. */
    public void recordReservation(Product product, MovementType type, int availableChange, String orderRef,
                                  String note, String actor) {
        movementRepository.save(new StockMovement(product, null, type, availableChange, null, product.getCostPrice(),
                null, null, orderRef, note, actor));
    }

    /** Warehouse row of a product, created on first use (safe: the caller holds the product's inventory lock). */
    WarehouseStock locate(Product product, Warehouse warehouse) {
        if (!warehouse.isOwnedBy(product.getSellerId())) {
            throw ApiException.badRequest("Warehouse " + warehouse.getCode() + " does not belong to this store");
        }
        return stockRepository.find(product.getId(), warehouse.getId())
                .orElseGet(() -> stockRepository.save(new WarehouseStock(product, warehouse)));
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw ApiException.badRequest("The quantity must be at least 1");
        }
    }
}
