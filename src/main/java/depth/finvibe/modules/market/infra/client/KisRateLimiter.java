package depth.finvibe.modules.market.infra.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class KisRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(KisRateLimiter.class);

  private static final long SECOND_WINDOW_MILLIS = 1000L;

  private static final RedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
      "local current = redis.call('INCR', KEYS[1]) " +
          "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
          "local ttl = redis.call('PTTL', KEYS[1]) " +
          "return {current, ttl}",
      List.class
  );

  private static final RedisScript<List> TRY_RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
      "local second = redis.call('get', KEYS[1]) " +
      "second = tonumber(second) or 0 " +
      "if second >= tonumber(ARGV[1]) then " +
      "return {0, redis.call('PTTL', KEYS[1])} " +
      "end " +
      "second = redis.call('INCR', KEYS[1]) " +
      "if second == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end " +
      "return {1, 0}",
      List.class
  );

  private final StringRedisTemplate redisTemplate;
  private final String keyPrefix;
  private final long secondLimit;

  public KisRateLimiter(
      StringRedisTemplate redisTemplate,
      @Value("${market.kis.rate-limit.key-prefix:kis:rate}") String keyPrefix,
      @Value("${market.kis.rate-limit.second:15}") long secondLimit
  ) {
    this.redisTemplate = redisTemplate;
    this.keyPrefix = keyPrefix;
    this.secondLimit = secondLimit;
  }

  public void acquire(String key) {
    String secondKey = keyPrefix + ":second:" + key;

    while (true) {
      RateResult secondResult = increment(secondKey, SECOND_WINDOW_MILLIS);

      long waitMillis = 0L;
      if (secondResult.count > secondLimit) {
        waitMillis = Math.max(waitMillis, secondResult.ttlMillis);
      }

      if (waitMillis <= 0L) {
        return;
      }

      sleep(waitMillis, key);
    }
  }

  public boolean tryAcquire(String key) {
    String secondKey = keyPrefix + ":second:" + key;
    List<Long> result = (List<Long>) redisTemplate.execute(
        TRY_RATE_LIMIT_SCRIPT,
        List.of(secondKey),
        String.valueOf(secondLimit),
        String.valueOf(SECOND_WINDOW_MILLIS)
    );

    if (result == null || result.isEmpty()) {
      throw new IllegalStateException("KIS rate limiter returned empty result.");
    }

    return result.get(0) != null && result.get(0) == 1L;
  }

  /**
   * 특정 AppKey를 레이트 리미트 초과 상태로 즉시 설정합니다.
   * <p>
   * KIS API 응답에서 "EGW00201" 에러 코드를 받은 경우 호출하여
   * 해당 AppKey의 카운터를 한도 이상으로 설정합니다.
   * </p>
   *
   * @param key AppKey
   */
  public void markAsExceeded(String key) {
    String secondKey = keyPrefix + ":second:" + key;
    // 한도를 초과하도록 카운터를 설정 (한도 + 1)
    redisTemplate.opsForValue().set(
        secondKey,
        String.valueOf(secondLimit + 1),
        java.time.Duration.ofMillis(SECOND_WINDOW_MILLIS)
    );
    log.warn("KIS rate limit exceeded - AppKey marked as rate limited: {}", maskAppKey(key));
  }

  private String maskAppKey(String appKey) {
    if (appKey == null || appKey.length() < 8) {
      return "***";
    }
    return appKey.substring(0, 4) + "****" + appKey.substring(appKey.length() - 4);
  }

  private RateResult increment(String key, long windowMillis) {
    List<Long> result = (List<Long>) redisTemplate.execute(
        RATE_LIMIT_SCRIPT,
        List.of(key),
        String.valueOf(windowMillis)
    );

    if (result == null || result.size() < 2) {
      throw new IllegalStateException("KIS rate limiter returned empty result.");
    }

    long count = result.get(0) == null ? 0L : result.get(0);
    long ttl = result.get(1) == null ? 0L : result.get(1);
    if (ttl < 0L) {
      ttl = windowMillis;
    }

    return new RateResult(count, ttl);
  }

  private void sleep(long waitMillis, String key) {
    if (waitMillis <= 0L) {
      return;
    }
    try {
      log.debug("KIS rate limit waiting {}ms for key={}", waitMillis, key);
      Thread.sleep(waitMillis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("KIS rate limit wait interrupted.", ex);
    }
  }

  private record RateResult(long count, long ttlMillis) {
  }
}
