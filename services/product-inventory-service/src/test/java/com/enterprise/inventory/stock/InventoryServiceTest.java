package com.enterprise.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductCache;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.catalog.SellerProductQuery;
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.purchasing.PurchaseOrderRepository;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
import com.enterprise.inventory.stock.WarehouseAllocationStrategy.Allocation;
import com.enterprise.inventory.warehouse.Warehouse;
import com.enterprise.inventory.warehouse.WarehouseRepository;
import com.enterprise.inventory.warehouse.WarehouseService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests of the stock rules (Mockito: no database, no Spring context). */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private WarehouseStockRepository stockRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrders;
    @Mock
    private WarehouseService warehouseService;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private SellerProductQuery productQuery;
    @Mock
    private StockLedger ledger;
    @Mock
    private ProductCache productCache;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private EntityManager entityManager;

    private InventoryService service;
    private Product laptop;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, reservationRepository, movementRepository, stockRepository,
                productRepository, purchaseOrders, warehouseService, warehouseRepository, productQuery, ledger,
                productCache, events, entityManager, Duration.ofMinutes(40));
        laptop = product(7L, "LAP-1", "4999.00", "4000.00");
    }

    private static Product product(long id, String sku, String price, String cost) {
        Product product = new Product(50L, "Gulf Gadgets", sku, "Laptop " + sku, new BigDecimal(price), new BigDecimal(cost));
        product.setListed(true);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @Test
    void reserveFailsWithConflictWhenStockIsInsufficient() {
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-1")).thenReturn(List.of());
        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(laptop));
        when(inventoryRepository.reserve(eq(7L), eq(3), any(Instant.class))).thenReturn(0);
        when(inventoryRepository.availableOf(7L)).thenReturn(Optional.of(2));

        assertThatThrownBy(() -> service.reserve(new ReserveRequest("ORD-1", List.of(new ReserveItem(7L, 3)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requested 3, available 2")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void inventoryOnlyProductsCannotBeOrdered() {
        laptop.setListed(false);
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-2")).thenReturn(List.of());
        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(laptop));

        assertThatThrownBy(() -> service.reserve(new ReserveRequest("ORD-2", List.of(new ReserveItem(7L, 1)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available for sale");
        verify(inventoryRepository, never()).reserve(any(), anyInt(), any());
    }

    @Test
    void reserveIsIdempotentForTheSameOrder() {
        StockReservation existing = new StockReservation("ORD-3", 7L, 2, new BigDecimal("4999.00"),
                new BigDecimal("4000.00"), Instant.now().plusSeconds(600));
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-3")).thenReturn(List.of(existing));
        when(productRepository.findByIdIn(List.of(7L))).thenReturn(List.of(laptop));

        var response = service.reserve(new ReserveRequest("ORD-3", List.of(new ReserveItem(7L, 2))));

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).sellerId()).isEqualTo(50L);
        verify(inventoryRepository, never()).reserve(any(), anyInt(), any());
    }

    @Test
    void productsOfOneOrderMustBelongToOneSeller() {
        Product other = new Product(60L, "Other Store", "OTH-1", "Other", new BigDecimal("10.00"), new BigDecimal("5.00"));
        other.setListed(true);
        ReflectionTestUtils.setField(other, "id", 9L);
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-4")).thenReturn(List.of());
        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(laptop));
        when(productRepository.findWithCategoryById(9L)).thenReturn(Optional.of(other));
        when(inventoryRepository.reserve(eq(7L), eq(1), any(Instant.class))).thenReturn(1);
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.availableOf(7L)).thenReturn(Optional.of(10));

        assertThatThrownBy(() -> service.reserve(new ReserveRequest("ORD-4",
                List.of(new ReserveItem(7L, 1), new ReserveItem(9L, 1)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("same seller");
    }

    @Test
    void vacationModeAndSuspensionHideAListedProduct() {
        assertThat(laptop.isPurchasable()).isTrue();
        laptop.setStoreVisibility(false, false);
        assertThat(laptop.isPurchasable()).as("vacation mode").isFalse();
        laptop.setStoreVisibility(true, true);
        assertThat(laptop.isPurchasable()).as("suspended by the administrator").isFalse();
        laptop.setStoreVisibility(true, false);
        laptop.archive();
        assertThat(laptop.isPurchasable()).as("archived").isFalse();
    }

    @Test
    void weightedAverageCostBlendsOldAndNewStock() {
        Product product = product(1L, "WAC-1", "200.00", "100.00");
        product.receiveStockAtCost(10, 10, new BigDecimal("130.00"));
        assertThat(product.getCostPrice()).isEqualByComparingTo("115.00");
        product.receiveStockAtCost(0, 5, new BigDecimal("90.00"));
        assertThat(product.getCostPrice()).isEqualByComparingTo("90.00");
    }

    @Test
    void discountIsOnlyShownWhenTheWasPriceIsHigher() {
        Product product = product(2L, "DISC-1", "75.00", "40.00");
        product.price(new BigDecimal("75.00"), new BigDecimal("100.00"), new BigDecimal("40.00"));
        assertThat(product.discountPercent()).isEqualTo(25);
        product.price(new BigDecimal("75.00"), new BigDecimal("70.00"), new BigDecimal("40.00"));
        assertThat(product.discountPercent()).isZero();
    }

    @Test
    void stockStatusFollowsTheReorderPoint() {
        assertThat(StockStatus.of(0, 5)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.of(5, 5)).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(StockStatus.of(6, 5)).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    void allocationShipsFromTheDefaultWarehouseFirstThenTheFullest() {
        Warehouse main = warehouse(1L, "MAIN", true);
        Warehouse dubai = warehouse(2L, "DXB", false);
        Warehouse sharjah = warehouse(3L, "SHJ", false);
        WarehouseStock inMain = stock(main, 2);
        WarehouseStock inDubai = stock(dubai, 3);
        WarehouseStock inSharjah = stock(sharjah, 10);

        List<Allocation> plan = new DefaultWarehouseFirstAllocation().allocate(List.of(inDubai, inMain, inSharjah), 6);

        assertThat(plan).extracting(a -> a.stock().getWarehouse().getCode()).containsExactly("MAIN", "SHJ");
        assertThat(plan).extracting(Allocation::quantity).containsExactly(2, 4);
    }

    @Test
    void allocationRefusesWhenTheLedgerIsOutOfBalance() {
        WarehouseStock only = stock(warehouse(1L, "MAIN", true), 1);
        assertThatThrownBy(() -> new DefaultWarehouseFirstAllocation().allocate(List.of(only), 2))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Warehouse warehouse(long id, String code, boolean isDefault) {
        Warehouse warehouse = new Warehouse(50L, code, code + " warehouse", null, null, isDefault);
        ReflectionTestUtils.setField(warehouse, "id", id);
        return warehouse;
    }

    private WarehouseStock stock(Warehouse warehouse, int onHand) {
        WarehouseStock stock = new WarehouseStock(laptop, warehouse);
        ReflectionTestUtils.setField(stock, "onHand", onHand);
        return stock;
    }
}
