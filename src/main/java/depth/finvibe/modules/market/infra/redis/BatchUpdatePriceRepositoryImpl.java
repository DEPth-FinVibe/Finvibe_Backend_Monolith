package depth.finvibe.modules.market.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.market.application.port.out.BatchUpdatePriceRepository;
import depth.finvibe.modules.market.domain.BatchUpdatePrice;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class BatchUpdatePriceRepositoryImpl implements BatchUpdatePriceRepository {

    private static final String KEY_PREFIX = "market:batch-price:";
    private static final Duration BATCH_PRICE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveAll(List<BatchUpdatePrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return;
        }

        prices.forEach(price -> {
            try {
                String value = objectMapper.writeValueAsString(price);
                redisTemplate.opsForValue().set(keyForStock(price.getStockId()), value, BATCH_PRICE_TTL);
            } catch (JacksonIOException ex) {
                throw new IllegalStateException("Failed to serialize batch update price", ex);
            }
        });
    }

    @Override
    public Optional<BatchUpdatePrice> findByStockId(Long stockId) {
        String value = redisTemplate.opsForValue().get(keyForStock(stockId));
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, BatchUpdatePrice.class));
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to deserialize batch update price", ex);
        }
    }

    @Override
    public List<BatchUpdatePrice> findByStockIds(List<Long> stockIds) {
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

    private String keyForStock(Long stockId) {
        return KEY_PREFIX + stockId;
    }

    private Stream<BatchUpdatePrice> deserializeSafely(String value) {
        try {
            return Stream.of(objectMapper.readValue(value, BatchUpdatePrice.class));
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to deserialize batch update price", ex);
        }
    }
}
