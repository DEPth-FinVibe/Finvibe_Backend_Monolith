package depth.finvibe.modules.asset.infra.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.asset.application.port.out.TopHoldingStockCacheRepository;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

@Repository
@RequiredArgsConstructor
public class TopHoldingStockCacheRepositoryImpl implements TopHoldingStockCacheRepository {
    private static final String KEY_PREFIX = "asset:top-holdings:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<TopHoldingStockDto.TopHoldingStockListResponse> find(UUID userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, TopHoldingStockDto.TopHoldingStockListResponse.class));
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to deserialize top holding stocks", ex);
        }
    }

    @Override
    public void save(UUID userId, TopHoldingStockDto.TopHoldingStockListResponse response) {
        try {
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(userId), value, CACHE_TTL);
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to serialize top holding stocks", ex);
        }
    }

    @Override
    public void evictByUserId(UUID userId) {
        String key = key(userId);
        redisTemplate.delete(key);
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
