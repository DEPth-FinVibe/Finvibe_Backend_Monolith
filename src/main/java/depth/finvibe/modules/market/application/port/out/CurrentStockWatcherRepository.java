package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.CurrentStockWatcher;
import java.util.List;

public interface CurrentStockWatcherRepository {
    void save(CurrentStockWatcher currentStockWatcher);
    void renew(CurrentStockWatcher currentStockWatcher);
    void remove(CurrentStockWatcher currentStockWatcher);

    boolean existsByStockId(Long stockId);
    boolean allExistsByStockIds(Iterable<Long> stockIds);

    List<Long> findActiveStockIds();
}