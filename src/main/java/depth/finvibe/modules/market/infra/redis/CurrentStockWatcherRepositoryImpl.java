package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentStockWatcherRepository;
import depth.finvibe.modules.market.domain.CurrentStockWatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CurrentStockWatcherRepositoryImpl implements CurrentStockWatcherRepository {

    private static final String KEY_PREFIX = "market:current-watcher:";
    private static final Duration INDEX_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(CurrentStockWatcher currentStockWatcher) {
        String key = keyForStock(currentStockWatcher.getStockId());
        redisTemplate.opsForSet().add(key, currentStockWatcher.getWatcherId().toString());
        redisTemplate.expire(key, INDEX_TTL);
    }

    @Override
    public void renew(CurrentStockWatcher currentStockWatcher) {
        String key = keyForStock(currentStockWatcher.getStockId());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.expire(key, INDEX_TTL);
        } else {
            save(currentStockWatcher);
        }
    }

    @Override
    public void remove(CurrentStockWatcher currentStockWatcher) {
        String key = keyForStock(currentStockWatcher.getStockId());
        redisTemplate.opsForSet().remove(key, currentStockWatcher.getWatcherId().toString());
        Long remaining = redisTemplate.opsForSet().size(key);
        if (remaining != null && remaining == 0L) {
            redisTemplate.delete(key);
        }
    }

    @Override
    public boolean existsByStockId(Long stockId) {
        String key = keyForStock(stockId);
        Long size = redisTemplate.opsForSet().size(key);
        return size != null && size > 0;
    }

    @Override
    public boolean allExistsByStockIds(Iterable<Long> stockIds) {
        for (Long stockId : stockIds) {
            if (!existsByStockId(stockId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Long> findActiveStockIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<Long> stockIds = new ArrayList<>();
        for (String key : keys) {
            if (key == null || !key.startsWith(KEY_PREFIX)) {
                continue;
            }
            String rawId = key.substring(KEY_PREFIX.length());
            try {
                stockIds.add(Long.parseLong(rawId));
            } catch (NumberFormatException ex) {
                log.warn("Invalid current watcher key: {}", key);
            }
        }
        return stockIds;
    }

    private String keyForStock(Long stockId) {
        return KEY_PREFIX + stockId;
    }
}
