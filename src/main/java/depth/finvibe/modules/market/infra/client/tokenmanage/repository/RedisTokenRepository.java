package depth.finvibe.modules.market.infra.client.tokenmanage.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Repository
public class RedisTokenRepository implements TokenRepository {
    private static final ZoneId KIS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String TOKEN_KEY_PREFIX = "kis:accessToken:";
    private static final String EXPIRES_AT_KEY_PREFIX = "kis:accessToken:expiresAt:";
    private static final String REFRESH_LOCK_KEY_PREFIX = "kis:accessToken:refreshLock:";
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public RedisTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getAccessToken(String appKey) {
        return redisTemplate.opsForValue().get(tokenKey(appKey));
    }

    @Override
    public LocalDateTime getExpiresAt(String appKey) {
        String expiresAtRaw = redisTemplate.opsForValue().get(expiresAtKey(appKey));
        if (expiresAtRaw == null) {
            return null;
        }
        try {
            long epochSeconds = Long.parseLong(expiresAtRaw);
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), KIS_ZONE);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void saveToken(String appKey, String token, LocalDateTime expiresAt) {
        long ttlSeconds = Math.max(0, Duration.between(LocalDateTime.now(KIS_ZONE), expiresAt).getSeconds());
        redisTemplate.opsForValue().set(tokenKey(appKey), token, Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForValue().set(
                expiresAtKey(appKey),
                String.valueOf(expiresAt.atZone(KIS_ZONE).toEpochSecond()),
                Duration.ofSeconds(ttlSeconds)
        );
    }

    @Override
    public void deleteToken(String appKey) {
        redisTemplate.delete(List.of(
                tokenKey(appKey),
                expiresAtKey(appKey)
        ));
    }

    @Override
    public boolean acquireRefreshLock(String appKey) {
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                refreshLockKey(appKey),
                lockValue,
                REFRESH_LOCK_TTL
        );
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void releaseRefreshLock(String appKey) {
        redisTemplate.delete(refreshLockKey(appKey));
    }

    private String tokenKey(String appKey) {
        return TOKEN_KEY_PREFIX + appKey;
    }

    private String expiresAtKey(String appKey) {
        return EXPIRES_AT_KEY_PREFIX + appKey;
    }

    private String refreshLockKey(String appKey) {
        return REFRESH_LOCK_KEY_PREFIX + appKey;
    }
}
