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
    // 감시 중인 종목 인덱스(member = 종목 ID, score = 만료 시각 epoch ms). 리스너도 같은 키를 갱신한다.
    // 전체 키를 훑는 KEYS는 마스터를 0.3~0.6초씩 멈춰 시세 경로를 막았다(#17 D25).
    static final String ACTIVE_INDEX_KEY = "market:current-watcher-index";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(CurrentStockWatcher currentStockWatcher) {
        String key = keyForStock(currentStockWatcher.getStockId());
        redisTemplate.opsForSet().add(key, currentStockWatcher.getWatcherId().toString());
        redisTemplate.expire(key, INDEX_TTL);
        touchIndex(currentStockWatcher.getStockId());
    }

    @Override
    public void renew(CurrentStockWatcher currentStockWatcher) {
        String key = keyForStock(currentStockWatcher.getStockId());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.expire(key, INDEX_TTL);
            touchIndex(currentStockWatcher.getStockId());
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
            redisTemplate.opsForZSet().remove(ACTIVE_INDEX_KEY, String.valueOf(currentStockWatcher.getStockId()));
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
        redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_INDEX_KEY, Double.NEGATIVE_INFINITY, System.currentTimeMillis());
        Set<String> members = redisTemplate.opsForZSet().range(ACTIVE_INDEX_KEY, 0, -1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Long> stockIds = new ArrayList<>(members.size());
        for (String member : members) {
            try {
                stockIds.add(Long.parseLong(member));
            } catch (NumberFormatException ex) {
                log.warn("Invalid current watcher index member: {}", member);
            }
        }
        return stockIds;
    }

    private void touchIndex(Long stockId) {
        redisTemplate.opsForZSet().add(ACTIVE_INDEX_KEY, String.valueOf(stockId),
                System.currentTimeMillis() + INDEX_TTL.toMillis());
    }

    private String keyForStock(Long stockId) {
        return KEY_PREFIX + "{stock:" + stockId + "}";
    }
}
