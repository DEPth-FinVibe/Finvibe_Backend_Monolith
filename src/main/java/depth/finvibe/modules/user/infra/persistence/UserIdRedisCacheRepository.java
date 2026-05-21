package depth.finvibe.modules.user.infra.persistence;

import depth.finvibe.modules.user.application.port.out.UserIdCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserIdRedisCacheRepository implements UserIdCacheRepository {

    private static final String KEY_PREFIX = "user:id-map:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<Long> findInternalUserIdByExternalUserId(UUID externalUserId) {
        String value = redisTemplate.opsForValue().get(key(externalUserId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void save(UUID externalUserId, Long internalUserId) {
        redisTemplate.opsForValue().set(key(externalUserId), internalUserId.toString());
    }

    private String key(UUID externalUserId) {
        return KEY_PREFIX + externalUserId;
    }
}
