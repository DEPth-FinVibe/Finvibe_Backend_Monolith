package depth.finvibe.modules.market.application;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;
import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.application.port.out.StockPriceEventProducer;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.CurrentPrice;
import depth.finvibe.modules.market.domain.CurrentStockWatcher;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentPriceService implements CurrentPriceCommandUseCase {

    private static final String STALE_TICK_METRIC = "market.current_price.stale_ticks";

    private final StockRepository stockRepository;
    private final HoldingStockRepository holdingStockRepository;
    private final CurrentStockWatcherRepository currentStockWatcherRepository;
    private final CurrentPriceRepository currentPriceRepository;
    private final CurrentPriceEventPublisher currentPriceEventPublisher;
    private final StockPriceEventProducer stockPriceEventProducer;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Long, BigDecimal> lastPublishedPrices = new ConcurrentHashMap<>();

    @Override
    public void registerWatchingStock(Long stockId, Long userId) {
        checkStockIsExist(stockId);

        currentStockWatcherRepository.save(CurrentStockWatcher.create(stockId, userId));
    }

    @Override
    public void renewWatchingStock(Long stockId, Long userId) {
        currentStockWatcherRepository.renew(CurrentStockWatcher.create(stockId, userId));
    }

    @Override
    public void unregisterWatchingStock(Long stockId, Long userId) {
        checkStockIsExist(stockId);

        currentStockWatcherRepository.remove(CurrentStockWatcher.create(stockId, userId));
    }

    @Override
    public void registerHoldingStock(Long stockId, Long userId) {
        checkStockIsExist(stockId);

        holdingStockRepository.registerHoldingStock(stockId, userId);
    }

    @Override
    public void unregisterHoldingStock(Long stockId, Long userId) {
        checkStockIsExist(stockId);

        holdingStockRepository.unregisterHoldingStock(stockId, userId);
    }

    @Override
    public void stockPriceUpdated(CurrentPriceUpdatedEvent priceUpdate) {
        // 두 경로로 갈라지기 전에 한 번만 버전을 받아 Pub/Sub과 Kafka에 같은 값을 싣는다.
        OptionalLong priceVersion = currentPriceRepository.saveIfNewer(CurrentPrice.from(priceUpdate));
        if (priceVersion.isEmpty()) {
            recordStaleTick(priceUpdate);
            return;
        }
        priceUpdate.setPriceVersion(priceVersion.getAsLong());
        priceUpdate.setPublishedAt(System.currentTimeMillis());
        currentPriceEventPublisher.publish(priceUpdate);
        produceIfPriceChanged(priceUpdate);
    }

    @Override
    public void stockPricesUpdated(List<CurrentPriceUpdatedEvent> priceUpdates) {
        if (priceUpdates.isEmpty()) {
            return;
        }
        List<OptionalLong> priceVersions = currentPriceRepository.saveAllIfNewer(
                priceUpdates.stream().map(CurrentPrice::from).toList());

        long publishedAt = System.currentTimeMillis();
        List<CurrentPriceUpdatedEvent> accepted = new ArrayList<>(priceUpdates.size());
        for (int i = 0; i < priceUpdates.size(); i++) {
            CurrentPriceUpdatedEvent priceUpdate = priceUpdates.get(i);
            OptionalLong priceVersion = priceVersions.get(i);
            if (priceVersion.isEmpty()) {
                recordStaleTick(priceUpdate);
                continue;
            }
            priceUpdate.setPriceVersion(priceVersion.getAsLong());
            priceUpdate.setPublishedAt(publishedAt);
            accepted.add(priceUpdate);
        }
        if (accepted.isEmpty()) {
            return;
        }
        currentPriceEventPublisher.publishAll(accepted);
        accepted.forEach(this::produceIfPriceChanged);
    }

    private void recordStaleTick(CurrentPriceUpdatedEvent priceUpdate) {
        meterRegistry.counter(STALE_TICK_METRIC).increment();
        log.debug("Skipped stale price tick. stockId={}, at={}", priceUpdate.getStockId(), priceUpdate.getAt());
    }

    private void produceIfPriceChanged(CurrentPriceUpdatedEvent priceUpdate) {
        BigDecimal newPrice = priceUpdate.getClose();
        BigDecimal prevPrice = lastPublishedPrices.get(priceUpdate.getStockId());

        if (prevPrice == null || prevPrice.compareTo(newPrice) != 0) {
            lastPublishedPrices.put(priceUpdate.getStockId(), newPrice);
            stockPriceEventProducer.publishStockPriceUpdated(StockPriceUpdatedEvent.builder()
                    .stockId(priceUpdate.getStockId())
                    .price(newPrice)
                    .updatedAt(priceUpdate.getAt() != null ? priceUpdate.getAt() : LocalDateTime.now())
                    .priceVersion(priceUpdate.getPriceVersion())
                    .build());
        }
    }


    private void checkStockIsExist(Long stockId) {
        if(!stockRepository.existsById(stockId)) {
            throw new DomainException(MarketErrorCode.STOCK_NOT_FOUND);
        }
    }


}
