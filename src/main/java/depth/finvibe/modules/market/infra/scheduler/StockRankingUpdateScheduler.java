package depth.finvibe.modules.market.infra.scheduler;

import depth.finvibe.modules.market.application.port.in.StockCommandUseCase;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockRankingUpdateScheduler {

    private final StockCommandUseCase stockCommandUseCase;
    private final StockRepository stockRepository;
    private static final long WAIT_INTERVAL_MS = 5000L;

    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(
            name = "stockBulkUpsert",
            lockAtMostFor = "PT1M",
            lockAtLeastFor = "PT5S"
    )
    public void executeStockRankingUpdate() {
        if (!waitForStockData()) {
            return;
        }
        stockCommandUseCase.renewStockCharts();
    }

    private boolean waitForStockData() {
        while (!stockRepository.existsAny()) {
            try {
                Thread.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
