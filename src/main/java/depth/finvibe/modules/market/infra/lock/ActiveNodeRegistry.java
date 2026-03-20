package depth.finvibe.modules.market.infra.lock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 분산 환경에서 활성 노드를 추적하고 관리하는 컴포넌트입니다.
 * Heartbeat 방식으로 각 노드의 활성 상태를 Redis에 기록하고,
 * 현재 활성화된 노드의 수를 조회할 수 있습니다.
 */
@Component
public class ActiveNodeRegistry {
	private static final Logger log = LoggerFactory.getLogger(ActiveNodeRegistry.class);

  private static final String NODE_KEY_PREFIX = "market:subscription-node:";
  private static final long HEARTBEAT_TTL_SECONDS = 15L;

  private final RedissonClient redissonClient;
  private String nodeId;

  public ActiveNodeRegistry(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  public String getNodeId() {
    return nodeId;
  }

  @PostConstruct
  public void initialize() {
    this.nodeId = UUID.randomUUID().toString();
    log.info("ActiveNodeRegistry 초기화 완료 - NodeId: {}", nodeId);
    recordHeartbeat();
  }

  /**
   * 애플리케이션 종료 시 현재 노드의 Heartbeat를 삭제합니다.
   * <p>
   * Graceful shutdown을 위해 Redis에서 현재 노드의 heartbeat 키를 즉시 삭제하여
   * 다른 노드가 정확한 활성 노드 수를 파악할 수 있도록 합니다.
   * </p>
   */
  @PreDestroy
  public void shutdown() {
    String key = NODE_KEY_PREFIX + nodeId;
    try {
      RBucket<Object> bucket = redissonClient.getBucket(key);
      if (bucket == null) {
        log.warn("ActiveNodeRegistry 종료 중 heartbeat 버킷을 찾지 못했습니다 - NodeId: {}", nodeId);
        return;
      }
      bucket.delete();
      log.info("ActiveNodeRegistry 종료 완료 - NodeId: {} heartbeat 삭제됨", nodeId);
    } catch (Exception ex) {
      log.error("ActiveNodeRegistry 종료 중 heartbeat 삭제 실패 - NodeId: {}", nodeId, ex);
    }
  }

  /**
   * 현재 노드의 Heartbeat를 갱신합니다.
   * 스케줄러에서 주기적으로 호출하여 이 노드가 활성 상태임을 알립니다.
   */
  @Scheduled(fixedDelayString = "${market.active-node.heartbeat-interval-ms:5000}")
  public void recordHeartbeat() {
    String key = NODE_KEY_PREFIX + nodeId;
    try {
      RBucket<Object> bucket = redissonClient.getBucket(key);
      if (bucket == null) {
        log.warn("Heartbeat 기록 실패 - 버킷을 찾지 못했습니다 - NodeId: {}", nodeId);
        return;
      }
      bucket.set(System.currentTimeMillis(), HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
      log.trace("Heartbeat 기록 완료 - NodeId: {}", nodeId);
    } catch (Exception ex) {
      log.error("Heartbeat 기록 실패 - NodeId: {}", nodeId, ex);
    }
  }

  /**
   * 현재 활성화된 노드의 수를 반환합니다.
   * Redis에서 TTL이 유효한 노드 키의 개수를 조회합니다.
   *
   * @return 활성 노드 수 (최소 1)
   */
  public int getActiveNodeCount() {
    try {
      Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(NODE_KEY_PREFIX + "*");
      int activeCount = 0;
      for (String key : keys) {
        activeCount++;
      }

      if (activeCount == 0) {
        log.warn("활성 노드가 0개로 조회되었습니다. 기본값 1을 반환합니다.");
        return 1;
      }

      log.debug("활성 노드 수: {}", activeCount);
      return activeCount;

    } catch (Exception ex) {
      log.error("활성 노드 수 조회 실패. 기본값 1을 반환합니다.", ex);
      return 1;
    }
  }

}
