package depth.finvibe.modules.market.infra.lock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
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
  // 활성 노드 목록. member = 노드 ID, score = 마지막 heartbeat 시각(epoch ms).
  // 노드 수를 셀 때 전체 키를 SCAN하지 않도록 이 집합 하나만 조회한다(#17 D12).
  static final String NODE_SET_KEY = "market:subscription-nodes";
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
      redissonClient.<String>getScoredSortedSet(NODE_SET_KEY).remove(nodeId);
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
      long now = System.currentTimeMillis();
      // 개별 키는 이전 버전 노드가 셀 수 있도록 롤링 배포 동안 함께 유지한다.
      bucket.set(now, HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
      redissonClient.<String>getScoredSortedSet(NODE_SET_KEY).add(now, nodeId);
      log.trace("Heartbeat 기록 완료 - NodeId: {}", nodeId);
    } catch (Exception ex) {
      log.error("Heartbeat 기록 실패 - NodeId: {}", nodeId, ex);
    }
  }

  /**
   * 현재 활성화된 노드의 수를 반환합니다.
   * 활성 노드 목록에서 TTL 안에 heartbeat를 남긴 노드 수를 조회합니다.
   *
   * @return 활성 노드 수 (최소 1)
   */
  public int getActiveNodeCount() {
    try {
      RScoredSortedSet<String> nodes = redissonClient.getScoredSortedSet(NODE_SET_KEY);
      long aliveSince = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(HEARTBEAT_TTL_SECONDS);
      // TTL 안에 heartbeat가 없는 노드는 목록에서 지운다.
      nodes.removeRangeByScore(0, true, aliveSince, false);
      int activeCount = nodes.size();

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
