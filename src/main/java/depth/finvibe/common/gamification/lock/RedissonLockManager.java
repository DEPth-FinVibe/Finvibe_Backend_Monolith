package depth.finvibe.common.gamification.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 기반 분산 락 구현체
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedissonLockManager implements DistributedLockManager {

  private static final String LOCK_PREFIX = "lock:";
  private static final Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(10);
  private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);

  private final RedissonClient redissonClient;

  @Override
  public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> task) {
    String fullLockKey = LOCK_PREFIX + lockKey;
    RLock lock = redissonClient.getLock(fullLockKey);

    boolean acquired = false;
    try {
      log.debug("Attempting to acquire lock: {}", fullLockKey);
      acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);

      if (!acquired) {
        log.warn("Failed to acquire lock: {} within {} ms", fullLockKey, waitTime.toMillis());
        throw new LockAcquisitionException(fullLockKey, waitTime.toMillis());
      }

      log.debug("Lock acquired: {}", fullLockKey);
      return task.get();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while acquiring lock: {}", fullLockKey, e);
      throw new LockAcquisitionException(fullLockKey, e);
    } finally {
      if (acquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.debug("Lock released: {}", fullLockKey);
      }
    }
  }

  @Override
  public <T> T executeWithLock(String lockKey, Supplier<T> task) {
    return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, task);
  }
}
