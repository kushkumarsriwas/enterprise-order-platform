package com.enterprise.inventory.catalog;

import com.enterprise.inventory.common.CriteriaPage;
import com.enterprise.inventory.common.Like;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.StockStatus;
import com.enterprise.inventory.stock.WarehouseStock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * The filter + sort engine behind the seller's product list, stock levels and exports. The product row is
 * joined with its inventory row so filters and sorting can use stock quantities and stock value.
 */
@Component
public class SellerProductQuery {

    public record Filter(String search, Long categoryId, Boolean listed, StockStatus stockStatus, Long supplierId,
                         String brand, Long warehouseId, BigDecimal minPrice, BigDecimal maxPrice, String sort) {

        public static Filter none() {
            return new Filter(null, null, null, null, null, null, null, null, null, null);
        }
    }

    private final EntityManager entityManager;

    public SellerProductQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<Product> find(Long sellerId, Filter filter, int page, int size) {
        return CriteriaPage.find(entityManager, Product.class, spec(sellerId, filter), page, size);
    }

    public List<Product> all(Long sellerId, Filter filter, int limit) {
        return CriteriaPage.list(entityManager, Product.class, spec(sellerId, filter), limit);
    }

    private static CriteriaPage.Spec<Product> spec(Long sellerId, Filter f) {
        return (cb, query, root, count) -> {
            Root<Inventory> inventory = query.from(Inventory.class);
            if (!count) {
                root.fetch("category", JoinType.LEFT);
                root.fetch("preferredSupplier", JoinType.LEFT);
            }
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(inventory.get("productId"), root.get("id")));
            if (sellerId != null) {
                where.add(cb.equal(root.get("sellerId"), sellerId)); // null = every seller (platform administrator)
            }
            where.add(cb.isTrue(root.get("active")));
            if (f.search() != null && !f.search().isBlank()) {
                for (String token : f.search().trim().split("\\s+")) {
                    String pattern = Like.contains(token);
                    where.add(cb.or(
                            cb.like(cb.lower(root.get("name")), pattern, Like.ESCAPE),
                            cb.like(cb.lower(root.get("sku")), pattern, Like.ESCAPE),
                            cb.like(cb.lower(cb.coalesce(root.get("barcode"), "")), pattern, Like.ESCAPE),
                            cb.like(cb.lower(cb.coalesce(root.get("brand"), "")), pattern, Like.ESCAPE)));
                }
            }
            if (f.categoryId() != null) {
                where.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.listed() != null) {
                where.add(cb.equal(root.get("listed"), f.listed()));
            }
            if (f.supplierId() != null) {
                where.add(cb.equal(root.get("preferredSupplier").get("id"), f.supplierId()));
            }
            if (f.brand() != null && !f.brand().isBlank()) {
                where.add(cb.equal(cb.lower(root.get("brand")), f.brand().trim().toLowerCase()));
            }
            if (f.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("price"), f.minPrice()));
            }
            if (f.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("price"), f.maxPrice()));
            }
            Expression<Integer> available = inventory.get("availableQuantity");
            if (f.stockStatus() != null) {
                where.add(switch (f.stockStatus()) {
                    case OUT_OF_STOCK -> cb.le(available, 0);
                    case LOW_STOCK -> cb.and(cb.gt(available, 0), cb.le(available, root.get("reorderPoint")));
                    case IN_STOCK -> cb.gt(available, root.get("reorderPoint"));
                });
            }
            if (f.warehouseId() != null) {
                Subquery<Long> inWarehouse = query.subquery(Long.class);
                Root<WarehouseStock> stock = inWarehouse.from(WarehouseStock.class);
                inWarehouse.select(stock.get("id")).where(cb.equal(stock.get("product"), root),
                        cb.equal(stock.get("warehouse").get("id"), f.warehouseId()));
                where.add(cb.exists(inWarehouse));
            }
            return CriteriaPage.Parts.of(where, count ? List.of() : order(cb, root, inventory, f.sort()));
        };
    }

    private static List<Order> order(CriteriaBuilder cb, Root<Product> root, Root<Inventory> inventory, String sort) {
        Expression<Integer> onHand = cb.sum(inventory.get("availableQuantity"), inventory.get("reservedQuantity"));
        Order primary = switch (sort == null ? "name" : sort) {
            case "sku" -> cb.asc(root.get("sku"));
            case "newest" -> cb.desc(root.get("createdAt"));
            case "updated" -> cb.desc(root.get("updatedAt"));
            case "price" -> cb.asc(root.get("price"));
            case "priceDesc" -> cb.desc(root.get("price"));
            case "available" -> cb.asc(inventory.get("availableQuantity"));
            case "availableDesc" -> cb.desc(inventory.get("availableQuantity"));
            case "value" -> cb.desc(cb.prod(onHand, root.get("costPrice")));
            case "bestselling" -> cb.desc(root.get("unitsSold"));
            case "margin" -> cb.desc(cb.diff(root.get("price"), root.get("costPrice")));
            default -> cb.asc(root.get("name"));
        };
        return List.of(primary, cb.asc(root.get("id")));
    }
}
