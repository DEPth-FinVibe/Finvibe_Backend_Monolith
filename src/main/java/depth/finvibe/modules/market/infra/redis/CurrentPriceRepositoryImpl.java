package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.domain.CurrentPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class CurrentPriceRepositoryImpl implements CurrentPriceRepository {

    private static final String KEY_PREFIX = "market:current-price:";
    private static final String UPDATED_AT_KEY_PREFIX = "market:current-price-updated-at:";
    private static final Duration CURRENT_PRICE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void upsertCurrentPrice(CurrentPrice currentPrice) {
        try {
            String value = objectMapper.writeValueAsString(currentPrice);
            redisTemplate.opsForValue().set(keyForStock(currentPrice.getStockId()), value, CURRENT_PRICE_TTL);
            redisTemplate.opsForValue().set(keyForUpdatedAt(currentPrice.getStockId()),
                    String.valueOf(Instant.now().toEpochMilli()), CURRENT_PRICE_TTL);
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to serialize current price", ex);
        }
    }

    @Override
    public void deleteCurrentPrice(Long stockId) {
        redisTemplate.delete(keyForStock(stockId));
        redisTemplate.delete(keyForUpdatedAt(stockId));
    }

    @Override
    public List<CurrentPrice> findByStockIds(List<Long> stockIds) {
        List<String> keys = stockIds.stream()
                .map(this::keyForStock)
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .flatMap(this::deserializeSafely)
                .toList();
    }

    @Override
    public Map<Long, LocalDateTime> findLastUpdatedAtByStockIds(List<Long> stockIds) {
        if (stockIds == null || stockIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = stockIds.stream()
                .map(this::keyForUpdatedAt)
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<Long, LocalDateTime> result = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            String raw = values.get(i);
            if (raw == null) {
                continue;
            }

            try {
                long epochMillis = Long.parseLong(raw);
                LocalDateTime updatedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("Asia/Seoul"));
                result.put(stockIds.get(i), updatedAt);
            } catch (NumberFormatException ex) {
                // 잘못된 값은 skip
            }
        }

        return result;
    }

    private String keyForStock(Long stockId) {
        return KEY_PREFIX + stockId;
    }

    private String keyForUpdatedAt(Long stockId) {
        return UPDATED_AT_KEY_PREFIX + stockId;
    }

    private Stream<CurrentPrice> deserializeSafely(String value) {
        try {
            return Stream.of(objectMapper.readValue(value, CurrentPrice.class));
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to deserialize current price", ex);
        }
    }
}

