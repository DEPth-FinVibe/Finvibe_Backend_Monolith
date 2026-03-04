package depth.finvibe.modules.market.infra.persistence;

import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceCandleJpaRepository extends JpaRepository<PriceCandle, Long> {
    List<PriceCandle> findByStockIdAndTimeframeAndAtBetweenOrderByAtAsc(
            Long stockId,
            Timeframe timeframe,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    List<PriceCandle> findByStockIdAndTimeframeAndAtIn(
            Long stockId,
            Timeframe timeframe,
            List<LocalDateTime> times
    );

    @Query("""
            select pc from PriceCandle pc
            where pc.stockId in :stockIds
            and pc.timeframe = :timeframe
            and pc.isMissing = false
            and pc.at = (
                select max(pc2.at) from PriceCandle pc2
                where pc2.stockId = pc.stockId
                and pc2.timeframe = :timeframe
                and pc2.isMissing = false
            )
            """)
    List<PriceCandle> findLatestByStockIdsAndTimeframe(
            @Param("stockIds") List<Long> stockIds,
            @Param("timeframe") Timeframe timeframe
    );

    List<PriceCandle> findByStockIdInAndTimeframeAndAtAndIsMissingFalse(
            List<Long> stockIds,
            Timeframe timeframe,
            LocalDateTime at
    );

    boolean existsByStockIdAndTimeframe(Long stockId, Timeframe timeframe);
}
