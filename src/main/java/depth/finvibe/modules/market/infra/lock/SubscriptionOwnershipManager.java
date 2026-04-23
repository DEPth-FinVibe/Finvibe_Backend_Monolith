package depth.finvibe.modules.market.infra.lock;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionOwnershipManager {

  private static final String OWNER_KEY_PREFIX = "market:subscription-owner:";
  private static final long OWNER_TTL_SECONDS = 15L;

  private final RedissonClient redissonClient;

  public boolean tryAcquireOwnership(Long stockId, String nodeId) {
    String key = ownerKey(stockId);
    try {
      boolean acquired = redissonClient.getBucket(key)
              .trySet(nodeId, OWNER_TTL_SECONDS, TimeUnit.SECONDS);
      if (acquired) {
        log.debug("구독 소유권 획득 - stockId: {}, nodeId: {}", stockId, nodeId);
      }
      return acquired;
    } catch (Exception ex) {
      log.error("구독 소유권 획득 실패 - stockId: {}, nodeId: {}", stockId, nodeId, ex);
      return false;
    }
  }

  public boolean isOwnedByNode(Long stockId, String nodeId) {
    String owner = getOwner(stockId);
    return nodeId.equals(owner);
  }

  public void renewOwnership(Long stockId, String nodeId) {
    RBucket<String> bucket = getBucket(stockId);
    try {
      String owner = bucket.get();
      if (!nodeId.equals(owner)) {
        return;
      }
      bucket.expire(OWNER_TTL_SECONDS, TimeUnit.SECONDS);
      log.trace("구독 소유권 갱신 - stockId: {}, nodeId: {}", stockId, nodeId);
    } catch (Exception ex) {
      log.error("구독 소유권 갱신 실패 - stockId: {}, nodeId: {}", stockId, nodeId, ex);
    }
  }

  public void releaseOwnership(Long stockId, String nodeId) {
    RBucket<String> bucket = getBucket(stockId);
    try {
      String owner = bucket.get();
      if (!nodeId.equals(owner)) {
        return;
      }
      bucket.delete();
      log.debug("구독 소유권 해제 - stockId: {}, nodeId: {}", stockId, nodeId);
    } catch (Exception ex) {
      log.error("구독 소유권 해제 실패 - stockId: {}, nodeId: {}", stockId, nodeId, ex);
    }
  }

  private RBucket<String> getBucket(Long stockId) {
    return redissonClient.getBucket(ownerKey(stockId));
  }

  private String getOwner(Long stockId) {
    try {
      return getBucket(stockId).get();
    } catch (Exception ex) {
      log.error("구독 소유권 조회 실패 - stockId: {}", stockId, ex);
      return null;
    }
  }

  private String ownerKey(Long stockId) {
    return OWNER_KEY_PREFIX + "{stock:" + stockId + "}";
  }
}
