package depth.finvibe.modules.market.infra.scheduler;

import depth.finvibe.modules.market.application.IndexMinuteCandleCacheService;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexMinuteCandleCacheScheduler {

    private final IndexMinuteCandleCacheService indexMinuteCandleCacheService;
    private MarketStatus lastMarketStatus;

    @Scheduled(cron = "${market.index-cache.cron:0 * * * * *}")
    @SchedulerLock(
            name = "indexMinuteCandleCache",
            lockAtMostFor = "PT1M",
            lockAtLeastFor = "PT5S"
    )
    public void cacheIndexMinuteCandles() {
        MarketStatus marketStatus = MarketHours.getCurrentStatus();
        logMarketStatusTransition(marketStatus);

        if (marketStatus != MarketStatus.OPEN) {
            return;
        }

        try {
            indexMinuteCandleCacheService.cacheLatestMinuteCandles();
        } catch (Exception e) {
            log.error("Failed to cache index minute candles", e);
        }
    }

    private void logMarketStatusTransition(MarketStatus currentStatus) {
        if (currentStatus == lastMarketStatus) {
            return;
        }

        if (currentStatus == MarketStatus.OPEN) {
            log.info("장 상태가 OPEN으로 전환되어 분봉 캐시를 재개합니다.");
        } else {
            log.info("장 상태가 OPEN이 아니어서 분봉 캐시를 대기합니다. 상태: {}", currentStatus);
        }
        lastMarketStatus = currentStatus;
    }
}
