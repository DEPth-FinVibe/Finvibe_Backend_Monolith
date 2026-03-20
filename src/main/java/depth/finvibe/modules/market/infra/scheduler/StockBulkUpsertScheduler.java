package depth.finvibe.modules.market.infra.scheduler;

import depth.finvibe.modules.market.application.port.in.StockCommandUseCase;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockBulkUpsertScheduler {

    private final StockCommandUseCase stockCommandUseCase;
    private final StockRepository stockRepository;
    private static final long WAIT_INTERVAL_MS = 5000L;

    public void executeStockBulkUpsert() {
        if (!waitForStockData()) {
            return;
        }
        stockCommandUseCase.bulkUpsertStocks();
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
