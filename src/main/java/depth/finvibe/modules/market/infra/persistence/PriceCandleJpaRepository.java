package depth.finvibe.modules.market.infra.persistence;

import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying
    @Query(value = """
            insert into price_candle (
                stock_id, timeframe, at, is_missing, open, high, low, close,
                prev_day_change_pct, volume, `value`
            ) values (
                :stockId, 'MINUTE', :at, false, :open, :high, :low, :close,
                :prevDayChangePct, :volume, :value
            )
            on duplicate key update
                is_missing = false,
                open = coalesce(open, values(open)),
                high = greatest(coalesce(high, values(high)), values(high)),
                low = least(coalesce(low, values(low)), values(low)),
                close = values(close),
                prev_day_change_pct = values(prev_day_change_pct),
                volume = greatest(coalesce(volume, 0), values(volume)),
                `value` = greatest(coalesce(`value`, 0), values(`value`))
            """, nativeQuery = true)
    void upsertRealtimeMinuteCandle(
            @Param("stockId") Long stockId,
            @Param("at") LocalDateTime at,
            @Param("open") java.math.BigDecimal open,
            @Param("high") java.math.BigDecimal high,
            @Param("low") java.math.BigDecimal low,
            @Param("close") java.math.BigDecimal close,
            @Param("prevDayChangePct") java.math.BigDecimal prevDayChangePct,
            @Param("volume") java.math.BigDecimal volume,
            @Param("value") java.math.BigDecimal value
    );
}
