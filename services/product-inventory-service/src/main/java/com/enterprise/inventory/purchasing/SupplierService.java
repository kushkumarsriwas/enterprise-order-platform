package com.enterprise.inventory.purchasing;

import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.common.Like;
import com.enterprise.inventory.common.PageResponse;
import com.enterprise.inventory.purchasing.PurchasingDtos.SupplierRequest;
import com.enterprise.inventory.purchasing.PurchasingDtos.SupplierResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A seller's suppliers with purchasing statistics (orders, spend, on-time delivery). */
@Service
public class SupplierService {

    private final SupplierRepository repository;
    private final PurchaseOrderRepository purchaseOrders;
    private final EntityManager entityManager;

    public SupplierService(SupplierRepository repository, PurchaseOrderRepository purchaseOrders,
                           EntityManager entityManager) {
        this.repository = repository;
        this.purchaseOrders = purchaseOrders;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(Long sellerId, String search, Boolean active, String sort, int page,
                                               int size) {
        Specification<Supplier> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("sellerId"), sellerId));
            if (active != null) {
                where.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = Like.contains(search);
                where.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("contactName"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern, Like.ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.get("city"), "")), pattern, Like.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Sort order = "newest".equals(sort) ? Sort.by(Sort.Direction.DESC, "createdAt")
                : "leadTime".equals(sort) ? Sort.by("leadTimeDays") : Sort.by("name");
        Page<Supplier> suppliers = repository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), order.and(Sort.by("id"))));
        Stats stats = stats(sellerId);
        return PageResponse.from(suppliers, s -> toResponse(s, stats));
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> all(Long sellerId) {
        Stats stats = stats(sellerId);
        return repository.findBySellerIdOrderByName(sellerId).stream().map(s -> toResponse(s, stats)).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long sellerId, Long id) {
        return toResponse(owned(sellerId, id), stats(sellerId));
    }

    @Transactional
    public SupplierResponse create(Long sellerId, SupplierRequest request) {
        String name = request.name().trim();
        if (repository.existsBySellerIdAndNameIgnoreCase(sellerId, name)) {
            throw ApiException.conflict("You already have a supplier named " + name);
        }
        Supplier supplier = new Supplier(sellerId, name);
        apply(supplier, request, name);
        return toResponse(repository.save(supplier), Stats.EMPTY);
    }

    @Transactional
    public SupplierResponse update(Long sellerId, Long id, SupplierRequest request) {
        Supplier supplier = owned(sellerId, id);
        String name = request.name().trim();
        if (repository.existsBySellerIdAndNameIgnoreCaseAndIdNot(sellerId, name, id)) {
            throw ApiException.conflict("You already have a supplier named " + name);
        }
        apply(supplier, request, name);
        return toResponse(supplier, stats(sellerId));
    }

    @Transactional
    public SupplierResponse setActive(Long sellerId, Long id, boolean active) {
        Supplier supplier = owned(sellerId, id);
        supplier.setActive(active);
        return toResponse(supplier, stats(sellerId));
    }

    /** Active supplier of the seller (for purchase orders and product planning data). */
    @Transactional(readOnly = true)
    public Supplier requireActive(Long sellerId, Long id) {
        Supplier supplier = owned(sellerId, id);
        if (!supplier.isActive()) {
            throw ApiException.conflict("Supplier " + supplier.getName() + " is inactive");
        }
        return supplier;
    }

    Supplier owned(Long sellerId, Long id) {
        return repository.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> ApiException.notFound("Supplier " + id + " not found"));
    }

    private static void apply(Supplier supplier, SupplierRequest r, String name) {
        supplier.update(name, trim(r.contactName()), trim(r.email()), trim(r.phone()), trim(r.address()),
                trim(r.city()), trim(r.country()) == null ? "United Arab Emirates" : trim(r.country()),
                r.leadTimeDays() == null ? 7 : r.leadTimeDays(), r.paymentTermsDays() == null ? 30 : r.paymentTermsDays(),
                trim(r.notes()));
    }

    private record Stats(Map<Long, Object[]> orders, Map<Long, Object[]> onTime, Map<Long, Long> products) {
        static final Stats EMPTY = new Stats(Map.of(), Map.of(), Map.of());
    }

    private Stats stats(Long sellerId) {
        Map<Long, Object[]> orders = new HashMap<>();
        purchaseOrders.statsBySupplier(sellerId, PurchaseOrderStatus.OPEN).forEach(row -> orders.put((Long) row[0], row));
        Map<Long, Object[]> onTime = new HashMap<>();
        purchaseOrders.onTimeBySupplier(sellerId).forEach(row -> onTime.put((Long) row[0], row));
        Map<Long, Long> products = new HashMap<>();
        entityManager.createQuery("""
                        select p.preferredSupplier.id, count(p) from Product p
                        where p.sellerId = :sellerId and p.active = true and p.preferredSupplier is not null
                        group by p.preferredSupplier.id""", Object[].class)
                .setParameter("sellerId", sellerId).getResultList()
                .forEach(row -> products.put((Long) row[0], ((Number) row[1]).longValue()));
        return new Stats(orders, onTime, products);
    }

    private static SupplierResponse toResponse(Supplier s, Stats stats) {
        Object[] orders = stats.orders().get(s.getId());
        Object[] onTime = stats.onTime().get(s.getId());
        Integer onTimePercent = null;
        if (onTime != null && ((Number) onTime[1]).longValue() > 0) {
            onTimePercent = (int) Math.round(((Number) onTime[2]).doubleValue() * 100 / ((Number) onTime[1]).doubleValue());
        }
        return new SupplierResponse(s.getId(), s.getName(), s.getContactName(), s.getEmail(), s.getPhone(),
                s.getAddress(), s.getCity(), s.getCountry(), s.getLeadTimeDays(), s.getPaymentTermsDays(), s.getNotes(),
                s.isActive(), orders == null ? 0 : ((Number) orders[1]).longValue(),
                orders == null ? 0 : ((Number) orders[2]).longValue(),
                orders == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(orders[3].toString()).setScale(2, RoundingMode.HALF_UP),
                onTimePercent, stats.products().getOrDefault(s.getId(), 0L), s.getCreatedAt());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
