package com.enterprise.inventory.stock;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Limit limit);

    List<StockMovement> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Limit limit);
}
