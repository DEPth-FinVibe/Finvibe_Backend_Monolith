package depth.finvibe.modules.trade.infra.persistence;

import depth.finvibe.modules.trade.domain.Trade;
import depth.finvibe.modules.trade.domain.enums.TradeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TradeJpaRepository extends JpaRepository<Trade, Long> {
    @Query("select distinct t.stockId from Trade t where t.userId = :userId and t.tradeType = :tradeType")
    List<Long> findDistinctStockIdsByUserIdAndTradeType(
            @Param("userId") UUID userId,
            @Param("tradeType") TradeType tradeType
    );

    List<Trade> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId, LocalDateTime start, LocalDateTime end
    );
}
