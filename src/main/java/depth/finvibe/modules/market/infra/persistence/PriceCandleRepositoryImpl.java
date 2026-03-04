package depth.finvibe.modules.market.infra.persistence;

import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PriceCandleRepositoryImpl implements PriceCandleRepository {

    private final PriceCandleJpaRepository jpaRepository;

    @Override
    public List<PriceCandle> findExisting(Long stockId, LocalDateTime startTime, LocalDateTime endTime, Timeframe timeframe) {
        LocalDateTime alignedStartTime = alignStartTime(startTime, timeframe);
        LocalDateTime alignedEndTime = alignStartTime(endTime, timeframe);
        return jpaRepository.findByStockIdAndTimeframeAndAtBetweenOrderByAtAsc(stockId, timeframe, alignedStartTime, alignedEndTime);
    }

    @Override
    public List<PriceCandle> findByStockIdAndTimeframeAndAtIn(Long stockId, Timeframe timeframe, List<LocalDateTime> times) {
        return jpaRepository.findByStockIdAndTimeframeAndAtIn(stockId, timeframe, times);
    }

    @Override
    public List<PriceCandle> findLatestByStockIdsAndTimeframe(List<Long> stockIds, Timeframe timeframe) {
        return jpaRepository.findLatestByStockIdsAndTimeframe(stockIds, timeframe);
    }

    @Override
    public List<PriceCandle> findByStockIdsAndTimeframeAndAt(List<Long> stockIds, Timeframe timeframe, LocalDateTime at) {
        if (stockIds == null || stockIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByStockIdInAndTimeframeAndAtAndIsMissingFalse(stockIds, timeframe, at);
    }

    @Override
    @Transactional
    public void saveAll(List<PriceCandle> fetchedResult) {
        jpaRepository.saveAll(fetchedResult);
    }

    @Override
    public boolean existsByStockIdAndTimeframe(Long stockId, Timeframe timeframe) {
        return jpaRepository.existsByStockIdAndTimeframe(stockId, timeframe);
    }

    private LocalDateTime alignStartTime(LocalDateTime startTime, Timeframe timeframe) {
        return timeframe.normalizeStart(startTime);
    }
}

