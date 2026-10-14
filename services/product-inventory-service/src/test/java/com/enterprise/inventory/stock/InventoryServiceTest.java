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
import com.enterprise.inventory.common.ApiException;
import com.enterprise.inventory.stock.StockDtos.ReserveItem;
import com.enterprise.inventory.stock.StockDtos.ReserveRequest;
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

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCache productCache;
    @Mock
    private ApplicationEventPublisher events;

    private InventoryService service;
    private final Product laptop = new Product("LAP-1", "Laptop", null, new BigDecimal("4999.00"), null, null);

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, reservationRepository, movementRepository,
                productRepository, productCache, events, Duration.ofMinutes(40));
    }

    @Test
    void reserveFailsWithConflictWhenStockIsInsufficient() {
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-1")).thenReturn(List.of());
        when(productRepository.findById(7L)).thenReturn(Optional.of(laptop));
        when(inventoryRepository.reserve(eq(7L), eq(3), any(Instant.class))).thenReturn(0);
        when(inventoryRepository.findById(7L)).thenReturn(Optional.of(new Inventory(laptop, 2, 1)));

        assertThatThrownBy(() -> service.reserve(new ReserveRequest("ORD-1", List.of(new ReserveItem(7L, 3)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requested 3, available 2")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveIsIdempotentForTheSameOrder() {
        StockReservation existing = new StockReservation("ORD-2", 7L, 1, Instant.now().plusSeconds(600));
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-2")).thenReturn(List.of(existing));
        when(productRepository.findById(7L)).thenReturn(Optional.of(laptop));

        var response = service.reserve(new ReserveRequest("ORD-2", List.of(new ReserveItem(7L, 1))));

        assertThat(response.lines()).hasSize(1);
        verify(inventoryRepository, never()).reserve(any(), anyInt(), any());
    }

    @Test
    void releasingAPaidOrderReturnsStockToTheShelf() {
        StockReservation committed = new StockReservation("ORD-3", 7L, 2, Instant.now().plusSeconds(600));
        committed.changeStatus(ReservationStatus.COMMITTED);
        when(reservationRepository.findByOrderRefOrderByProductId("ORD-3")).thenReturn(List.of(committed));
        when(productRepository.findById(7L)).thenReturn(Optional.of(laptop));

        service.release("ORD-3");

        verify(inventoryRepository).addStock(eq(7L), eq(2), any(Instant.class));
        assertThat(committed.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }
}
