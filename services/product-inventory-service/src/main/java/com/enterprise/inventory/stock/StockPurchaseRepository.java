package com.enterprise.inventory.stock;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockPurchaseRepository extends JpaRepository<StockPurchase, Long> {

    List<StockPurchase> findByMerchantIdAndPurchasedAtBetweenOrderByPurchasedAtDesc(Long merchantId, Instant from,
                                                                                     Instant to);
}
