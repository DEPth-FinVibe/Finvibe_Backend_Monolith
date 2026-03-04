package depth.finvibe.modules.market.infra.lock;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockSubscriptionLockManager {

    private static final String LOCK_PREFIX = "market:subscription-lock:";
    private static final long LOCK_WAIT_TIME_SECONDS = 0L;
    private static final long LOCK_LEASE_TIME_SECONDS = 10L;  // 30초 -> 10초로 단축

    private final RedissonClient redissonClient;

    /**
     * 종목에 대한 구독 Lock을 획득 시도합니다.
     *
     * @param stockId 종목 ID
     * @return Lock 획득 성공 여부
     */
    public boolean tryAcquireLock(Long stockId) {
        String lockKey = LOCK_PREFIX + stockId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);
            if (acquired) {
                log.trace("종목 구독 Lock 획득 성공 - stockId: {}, lockKey: {}", stockId, lockKey);
            } else {
                log.trace("종목 구독 Lock 획득 실패 (다른 노드가 보유 중) - stockId: {}", stockId);
            }
            return acquired;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("종목 구독 Lock 획득 중 인터럽트 발생 - stockId: {}", stockId, ex);
            return false;
        }
    }

    /**
     * 종목에 대한 구독 Lock을 해제합니다.
     *
     * @param stockId 종목 ID
     */
    public void releaseLock(Long stockId) {
        String lockKey = LOCK_PREFIX + stockId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.trace("종목 구독 Lock 해제 성공 - stockId: {}, lockKey: {}", stockId, lockKey);
            }
        } catch (IllegalMonitorStateException ex) {
            log.warn("Lock 해제 실패 (현재 스레드가 보유하지 않음) - stockId: {}", stockId);
        }
    }

    /**
     * 주어진 종목 목록에 포함되지 않은 Lock들을 해제합니다.
     *
     * @param activeStockIds 유지할 종목 ID 목록
     */
    public void releaseLocksNotIn(Collection<Long> activeStockIds) {
        // Redisson은 현재 스레드가 보유한 Lock 목록을 직접 조회하는 API를 제공하지 않으므로,
        // 이 메서드는 호출자가 명시적으로 관리하는 방식으로 구현되어야 합니다.
        // 따라서 Scheduler에서 직접 관리하도록 수정합니다.
    }
}
