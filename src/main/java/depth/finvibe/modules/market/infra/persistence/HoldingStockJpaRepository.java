package depth.finvibe.modules.market.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import depth.finvibe.modules.market.domain.HoldingStock;

public interface HoldingStockJpaRepository extends JpaRepository<HoldingStock, Long> {
    Optional<HoldingStock> findByStockIdAndUserId(Long stockId, UUID userId);
    
    @Query("SELECT DISTINCT h.stockId FROM HoldingStock h")
    List<Long> findAllDistinctStockIds();
}
