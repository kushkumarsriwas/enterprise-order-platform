package com.enterprise.inventory.warehouse;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long>, JpaSpecificationExecutor<StockTransfer> {

    @EntityGraph(attributePaths = {"fromWarehouse", "toWarehouse", "lines"})
    Optional<StockTransfer> findWithLinesByIdAndSellerId(Long id, Long sellerId);
}
