package depth.finvibe.modules.market.infra.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.domain.error.KisRateLimitExceededException;
import depth.finvibe.modules.market.infra.config.KisCredentialsProperties;
import depth.finvibe.modules.market.infra.config.KisCredentialsProperties.Credential;
import depth.finvibe.modules.market.infra.lock.ActiveNodeRegistry;

/**
 * KIS API Credential 할당 관리자
 * <p>
 * 여러 서버 인스턴스(분산 환경)에서 KIS API 인증 정보(Credential)를 공평하게 분배하고 관리합니다.
 * Redis 기반 분산 락을 사용하여 각 노드가 사용할 수 있는 Credential을 동적으로 할당합니다.
 * </p>
 *
 * <h3>주요 기능</h3>
 * <ul>
 *   <li>분산 환경에서 Credential 자동 할당 및 재분배</li>
 *   <li>노드 수에 따른 동적 부하 분산</li>
 *   <li>Rate Limit을 고려한 Credential 선택</li>
 *   <li>주기적인 락 갱신 및 재조정</li>
 * </ul>
 *
 * <h3>동작 방식</h3>
 * <ol>
 *   <li>초기화 시 활성 노드 수를 기반으로 목표 Credential 개수 계산</li>
 *   <li>Redis 분산 락을 사용하여 Credential 점유</li>
 *   <li>주기적으로(기본 20초) 락 갱신 및 재조정 실행</li>
 *   <li>API 요청 시 Rate Limit을 고려하여 가용한 Credential 선택</li>
 * </ol>
 *
 * <h3>예시</h3>
 * <pre>
 * // 3개의 Credential이 있고, 2개의 서버 인스턴스가 실행 중인 경우:
 * - 서버 A: Credential 2개 할당 (ceil(3/2) = 2)
 * - 서버 B: Credential 1개 할당
 * 
 * // API 요청 시:
 * Credential credential = allocator.selectCredentialForRequest(rateLimiter);
 * // Rate Limit이 여유있는 Credential을 라운드로빈 방식으로 선택
 * </pre>
 */
@Slf4j
@Component
public class KisCredentialAllocator {
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    private static final Duration DEFAULT_RENEW_INTERVAL = Duration.ofSeconds(20);
    private static final int DEFAULT_RETRY_MAX = 50;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 200L;
    private static final String DEFAULT_KEY_PREFIX = "kis:credential:lock";

    private static final RedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "else return 0 end",
            Long.class
    );

    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end",
            Long.class
    );

    private final KisCredentialsProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler taskScheduler;
    private final ActiveNodeRegistry activeNodeRegistry;
    private final String ownerId;
    private final AtomicInteger cursor = new AtomicInteger();

    private final Map<String, Credential> allocatedCredentials = new ConcurrentHashMap<>();

    public KisCredentialAllocator(
            KisCredentialsProperties properties,
            StringRedisTemplate redisTemplate,
            TaskScheduler taskScheduler,
            ActiveNodeRegistry activeNodeRegistry
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.taskScheduler = taskScheduler;
        this.activeNodeRegistry = activeNodeRegistry;
        this.ownerId = UUID.randomUUID().toString();
    }

  /**
   * 애플리케이션 시작 시 Credential 할당 및 갱신 스케줄러를 시작합니다.
   * <p>
   * 초기 할당을 수행하고, 이후 주기적으로 락 갱신 및 재조정을 실행합니다.
   * </p>
   */
  @PostConstruct
  public void init() {
    rebalanceAndRenew();
    taskScheduler.scheduleAtFixedRate(this::rebalanceAndRenew, resolveRenewInterval());
  }

  /**
   * 애플리케이션 종료 시 할당된 모든 Credential 락을 해제합니다.
   * <p>
   * Graceful shutdown을 위해 이 노드가 점유한 모든 락을 명시적으로 해제합니다.
   * TTL로 자동 해제되기를 기다리지 않고 즉시 해제하여 다른 노드가 빠르게 재할당할 수 있도록 합니다.
   * </p>
   */
  @PreDestroy
  public void destroy() {
    log.info("KisCredentialAllocator 종료 시작 - 할당된 credential 락 해제 중...");
    int releasedCount = 0;
    for (String appKey : List.copyOf(allocatedCredentials.keySet())) {
      if (releaseLock(appKey)) {
        allocatedCredentials.remove(appKey);
        releasedCount++;
        log.info("Credential 락 해제 완료 - appKey={}", maskAppKey(appKey));
      } else {
        log.warn("Credential 락 해제 실패 - appKey={} (이미 만료되었거나 다른 노드가 점유)", maskAppKey(appKey));
      }
    }
    log.info("KisCredentialAllocator 종료 완료 - 해제된 락 개수: {}", releasedCount);
  }

  /**
   * 현재 노드에 할당된 모든 Credential 목록을 반환합니다.
   * <p>
   * 유효한 Credential 중 이 노드가 점유하고 있는 것만 반환합니다.
   * </p>
   *
   * @return 할당된 Credential 목록 (읽기 전용)
   */
  public List<Credential> getAllocatedCredentials() {
        List<Credential> validCredentials = properties.getValidCredentials();
        if (validCredentials.isEmpty()) {
            return List.of();
        }

        List<Credential> ordered = new ArrayList<>();
        for (Credential credential : validCredentials) {
            if (allocatedCredentials.containsKey(credential.appKey())) {
                ordered.add(credential);
            }
        }
        return List.copyOf(ordered);
    }

    /**
   * API 요청에 사용할 Credential을 선택합니다.
   * <p>
   * Rate Limit을 고려하여 가용한 Credential을 라운드로빈 방식으로 선택합니다.
   * 모든 Credential이 Rate Limit에 도달한 경우, 예외를 발생시킵니다.
   * </p>
   *
   * @param rateLimiter Rate Limit 체크를 위한 KisRateLimiter
   * @return 선택된 Credential
   * @throws IllegalStateException 할당된 Credential이 없는 경우
   * @throws depth.finvibe.modules.market.domain.error.KisRateLimitExceededException 모든 Credential이 Rate Limit에 도달한 경우
   */
  public Credential selectCredentialForRequest(KisRateLimiter rateLimiter) {
        List<Credential> candidates = getAllocatedCredentials();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("KIS credential is not allocated yet.");
        }

        int size = candidates.size();
        int start = Math.floorMod(cursor.getAndIncrement(), size);
        for (int i = 0; i < size; i++) {
            int index = (start + i) % size;
            Credential credential = candidates.get(index);
            if (rateLimiter.tryAcquire(credential.appKey())) {
                return credential;
            }
        }

        log.warn("모든 KIS credential이 레이트 리미트에 도달했습니다. 할당된 credential 수: {}", size);
        throw new KisRateLimitExceededException(
                "모든 KIS API 키가 레이트 리미트에 도달했습니다. 잠시 후 다시 시도해주세요."
        );
    }

    /**
   * Credential 재조정 및 락 갱신을 수행합니다.
   * <p>
   * 다음 작업을 순서대로 실행합니다:
   * </p>
   * <ol>
   *   <li>기존에 할당된 락 갱신</li>
   *   <li>활성 노드 수 기반으로 목표 Credential 개수 계산</li>
   *   <li>목표 개수에 맞춰 Credential 추가 할당 또는 해제</li>
   * </ol>
   *
   * @throws IllegalStateException 유효한 Credential이 없거나 할당에 실패한 경우
   */
  private void rebalanceAndRenew() {
        List<Credential> validCredentials = properties.getValidCredentials();
        if (validCredentials.isEmpty()) {
            log.error("유효한 KIS credential이 없습니다. application-prod.yml의 KIS_API_KEY 환경 변수들을 확인하세요.");
            throw new IllegalStateException("최소 하나의 유효한 KIS credential이 필요합니다");
        }

        log.debug("유효한 credential 개수: {}", validCredentials.size());
        renewAllocatedLocks();
        int targetCount = calculateTargetCount(validCredentials.size());
        log.debug("목표 credential 할당 개수: {}, 활성 노드 수: {}", targetCount, activeNodeRegistry.getActiveNodeCount());
        adjustAllocation(validCredentials, targetCount);

        if (allocatedCredentials.isEmpty()) {
            log.error("KIS credential 할당 실패. Redis 연결 상태 및 다른 노드의 락 점유 상태를 확인하세요. 유효한 credential: {}", validCredentials.size());
            throw new IllegalStateException("KIS credential allocation failed. No credentials assigned.");
        }
        log.debug("KIS credential 할당 성공 - 할당된 개수: {}/{}", allocatedCredentials.size(), validCredentials.size());
    }

    /**
   * 현재 할당된 모든 Credential의 락을 갱신합니다.
   * <p>
   * 락 갱신에 실패한 Credential은 할당 목록에서 제거됩니다.
   * </p>
   */
  private void renewAllocatedLocks() {
        for (String appKey : List.copyOf(allocatedCredentials.keySet())) {
            if (!renewLock(appKey)) {
                allocatedCredentials.remove(appKey);
            }
        }
    }

    /**
   * 목표 Credential 개수에 맞춰 할당을 조정합니다.
   * <p>
   * 현재 할당 개수가 목표보다 많으면 일부를 해제하고,
   * 적으면 추가로 획득을 시도합니다.
   * </p>
   *
   * @param validCredentials 사용 가능한 모든 Credential 목록
   * @param targetCount 목표 Credential 개수
   */
  private void adjustAllocation(List<Credential> validCredentials, int targetCount) {
        int currentCount = allocatedCredentials.size();
        if (currentCount > targetCount) {
            releaseExtraCredentials(validCredentials, currentCount - targetCount);
        }
        if (allocatedCredentials.size() < targetCount) {
            acquireMissingCredentials(validCredentials, targetCount - allocatedCredentials.size());
        }
    }

    /**
   * 초과 할당된 Credential을 해제합니다.
   * <p>
   * 할당 목록의 뒤쪽부터 순서대로 해제합니다.
   * </p>
   *
   * @param validCredentials 사용 가능한 모든 Credential 목록
   * @param releaseCount 해제할 Credential 개수
   */
  private void releaseExtraCredentials(List<Credential> validCredentials, int releaseCount) {
        List<Credential> allocated = new ArrayList<>();
        for (Credential credential : validCredentials) {
            if (allocatedCredentials.containsKey(credential.appKey())) {
                allocated.add(credential);
            }
        }

        for (int i = allocated.size() - 1; i >= 0 && releaseCount > 0; i--) {
            Credential credential = allocated.get(i);
            if (releaseLock(credential.appKey())) {
                allocatedCredentials.remove(credential.appKey());
                releaseCount--;
                log.info("Released KIS credential lock - appKey={}", maskAppKey(credential.appKey()));
            }
        }
    }

    /**
   * 부족한 Credential을 추가로 획득합니다.
   * <p>
   * 최대 재시도 횟수까지 반복하며, 각 시도마다 모든 Credential에 대해 획득을 시도합니다.
   * </p>
   *
   * @param validCredentials 사용 가능한 모든 Credential 목록
   * @param acquireCount 획득할 Credential 개수
   */
  private void acquireMissingCredentials(List<Credential> validCredentials, int acquireCount) {
        log.debug("부족한 credential 획득 시도 - 목표 개수: {}", acquireCount);
        int attempts = 0;
        int maxRetries = resolveRetryMax();
        while (acquireCount > 0 && attempts++ < maxRetries) {
            for (Credential credential : validCredentials) {
                if (allocatedCredentials.containsKey(credential.appKey())) {
                    continue;
                }

                if (tryAcquire(credential)) {
                    acquireCount--;
                    if (acquireCount == 0) {
                        return;
                    }
                }
            }
            
            if (acquireCount > 0 && attempts % 10 == 0) {
                log.warn("Credential 획득 재시도 중 - 시도 횟수: {}/{}, 남은 개수: {}", attempts, maxRetries, acquireCount);
            }
            
            sleep(resolveRetryDelayMillis());
        }
        
        if (acquireCount > 0) {
            log.warn("최대 재시도 횟수 도달 - 획득하지 못한 credential 개수: {}", acquireCount);
        }
    }

    /**
   * Redis 분산 락을 사용하여 Credential 획득을 시도합니다.
   *
   * @param credential 획득할 Credential
   * @return 성공 시 true, 실패 시 false
   */
  private boolean tryAcquire(Credential credential) {
        String key = lockKey(credential.appKey());
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key,
                ownerId,
                resolveTtl()
        );
        if (Boolean.TRUE.equals(acquired)) {
            allocatedCredentials.put(credential.appKey(), credential);
            log.info("Allocated KIS credential lock - appKey={}", maskAppKey(credential.appKey()));
            return true;
        }
        log.trace("Failed to acquire credential lock - appKey={} (이미 다른 노드가 점유 중)", maskAppKey(credential.appKey()));
        return false;
    }

    private String maskAppKey(String appKey) {
        if (appKey == null || appKey.length() < 8) {
            return "***";
        }
        return appKey.substring(0, 4) + "****" + appKey.substring(appKey.length() - 4);
    }

    /**
   * Credential의 Redis 락 TTL을 갱신합니다.
   *
   * @param appKey 갱신할 Credential의 appKey
   * @return 성공 시 true, 실패 시 false (락을 다른 노드가 소유한 경우)
   */
  private boolean renewLock(String appKey) {
        Long result = redisTemplate.execute(
                RENEW_LOCK_SCRIPT,
                List.of(lockKey(appKey)),
                ownerId,
                String.valueOf(resolveTtl().toMillis())
        );
        return result != null && result == 1L;
    }

    /**
   * Credential의 Redis 락을 해제합니다.
   *
   * @param appKey 해제할 Credential의 appKey
   * @return 성공 시 true, 실패 시 false (락을 다른 노드가 소유한 경우)
   */
  private boolean releaseLock(String appKey) {
        Long result = redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                List.of(lockKey(appKey)),
                ownerId
        );
        return result != null && result == 1L;
    }

    /**
   * 현재 노드가 획득해야 할 목표 Credential 개수를 계산합니다.
   * <p>
   * 전체 Credential 수를 활성 노드 수로 나눈 값의 올림(ceil)을 반환합니다.
   * 예: Credential 3개, 노드 2개 → ceil(3/2) = 2
   * </p>
   *
   * @param credentialCount 전체 Credential 개수
   * @return 목표 Credential 개수
   */
  private int calculateTargetCount(int credentialCount) {
        int activeNodeCount = activeNodeRegistry.getActiveNodeCount();
        int target = (int) Math.ceil((double) credentialCount / activeNodeCount);
        return Math.min(target, credentialCount);
    }

    private Duration resolveTtl() {
        KisCredentialsProperties.CredentialLock lockConfig = properties.credentialLock();
        if (lockConfig == null || lockConfig.ttlSeconds() == null || lockConfig.ttlSeconds() <= 0) {
            return DEFAULT_TTL;
        }
        return Duration.ofSeconds(lockConfig.ttlSeconds());
    }

    private Duration resolveRenewInterval() {
        KisCredentialsProperties.CredentialLock lockConfig = properties.credentialLock();
        if (lockConfig == null || lockConfig.renewIntervalSeconds() == null || lockConfig.renewIntervalSeconds() <= 0) {
            return DEFAULT_RENEW_INTERVAL;
        }
        return Duration.ofSeconds(lockConfig.renewIntervalSeconds());
    }

    private int resolveRetryMax() {
        KisCredentialsProperties.CredentialLock lockConfig = properties.credentialLock();
        if (lockConfig == null || lockConfig.retryMax() == null || lockConfig.retryMax() <= 0) {
            return DEFAULT_RETRY_MAX;
        }
        return lockConfig.retryMax();
    }

    private long resolveRetryDelayMillis() {
        KisCredentialsProperties.CredentialLock lockConfig = properties.credentialLock();
        if (lockConfig == null || lockConfig.retryDelayMillis() == null || lockConfig.retryDelayMillis() <= 0) {
            return DEFAULT_RETRY_DELAY_MILLIS;
        }
        return lockConfig.retryDelayMillis();
    }

    private String lockKey(String appKey) {
        KisCredentialsProperties.CredentialLock lockConfig = properties.credentialLock();
        String prefix = DEFAULT_KEY_PREFIX;
        if (lockConfig != null && lockConfig.keyPrefix() != null && !lockConfig.keyPrefix().isBlank()) {
            prefix = lockConfig.keyPrefix();
        }
        return prefix + ":" + appKey;
    }

    private void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KIS credential allocation interrupted.", ex);
        }
    }
}
