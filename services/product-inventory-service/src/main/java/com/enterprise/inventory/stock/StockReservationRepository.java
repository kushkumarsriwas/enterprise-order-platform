package com.enterprise.inventory.stock;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderRefOrderByProductId(String orderRef);

    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant time, Limit limit);
}
