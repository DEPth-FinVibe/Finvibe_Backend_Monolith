package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceRepository;
import depth.finvibe.modules.market.domain.CurrentPrice;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class CurrentPriceRepositoryImpl implements CurrentPriceRepository {

    private static final String KEY_PREFIX = "market:current-price:";
    private static final String UPDATED_AT_KEY_PREFIX = "market:current-price-updated-at:";
    private static final String VERSION_KEY_PREFIX = "market:current-price-version:";
    private static final Duration CURRENT_PRICE_TTL = Duration.ofMinutes(5);
    // 틱이 뜸한 종목도 주말·연휴를 넘겨 단조성을 유지하도록 현재가보다 길게 둔다.
    private static final Duration PRICE_VERSION_TTL = Duration.ofDays(7);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    // priceVersion = 체결시각(epoch 초) × 10^6 + 같은 초 안 순번.
    // 세 키는 모두 {stock:<id>} hash tag라 같은 slot이다. 저장된 버전보다 이른 초면 아무것도 쓰지 않는다.
    // JSON은 파싱하지 않고 끝의 '}' 앞에 priceVersion을 붙인다. 가격 표현을 cjson으로 다시 인코딩하지 않기 위해서다.
    // KEYS: 1=현재가, 2=갱신 시각, 3=버전 / ARGV: 1=현재가 JSON, 2=체결 epoch 초, 3=현재 epoch ms, 4=현재가 TTL ms, 5=버전 TTL ms
    // 반환: 부여한 버전(문자열), 오래된 틱이면 빈 문자열
    private static final String SAVE_IF_NEWER_LUA = """
            local base = tonumber(ARGV[2]) * 1000000
            local stored = tonumber(redis.call('GET', KEYS[3]) or '0')
            local version
            if base > stored then
                version = base
            elseif stored - (stored % 1000000) == base then
                version = stored + 1
            else
                return ''
            end
            local encoded = string.format('%.0f', version)
            local json = string.sub(ARGV[1], 1, -2) .. ',"priceVersion":' .. encoded .. '}'
            redis.call('SET', KEYS[1], json, 'PX', ARGV[4])
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
            redis.call('SET', KEYS[3], encoded, 'PX', ARGV[5])
            return encoded
            """;
    private static final RedisScript<String> SAVE_IF_NEWER_SCRIPT = new DefaultRedisScript<>(SAVE_IF_NEWER_LUA, String.class);
    private static final String SAVE_IF_NEWER_SHA = SAVE_IF_NEWER_SCRIPT.getSha1();
    private static final Duration PIPELINE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration LOCAL_CACHE_TTL = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MarketRedisPipeline marketRedisPipeline;
    private final Map<Long, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    @Override
    public OptionalLong saveIfNewer(CurrentPrice currentPrice) {
        String value;
        try {
            value = objectMapper.writeValueAsString(currentPrice);
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to serialize current price", ex);
        }

        Long stockId = currentPrice.getStockId();
        String assigned = redisTemplate.execute(
                SAVE_IF_NEWER_SCRIPT,
                List.of(keyForStock(stockId), keyForUpdatedAt(stockId), keyForVersion(stockId)),
                value,
                String.valueOf(tickEpochSecond(currentPrice.getAt())),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(CURRENT_PRICE_TTL.toMillis()),
                String.valueOf(PRICE_VERSION_TTL.toMillis())
        );
        if (assigned == null || assigned.isEmpty()) {
            return OptionalLong.empty();
        }

        long priceVersion = Long.parseLong(assigned);
        putLocalCache(currentPrice.withPriceVersion(priceVersion));
        return OptionalLong.of(priceVersion);
    }

    @Override
    public List<OptionalLong> saveAllIfNewer(List<CurrentPrice> currentPrices) {
        if (currentPrices.isEmpty()) {
            return List.of();
        }
        if (!marketRedisPipeline.isAvailable()) {
            return currentPrices.stream().map(this::saveIfNewer).toList();
        }

        long nowMillis = Instant.now().toEpochMilli();
        List<String[]> keys = new ArrayList<>(currentPrices.size());
        List<String[]> args = new ArrayList<>(currentPrices.size());
        for (CurrentPrice currentPrice : currentPrices) {
            Long stockId = currentPrice.getStockId();
            keys.add(new String[]{keyForStock(stockId), keyForUpdatedAt(stockId), keyForVersion(stockId)});
            args.add(new String[]{
                    serialize(currentPrice),
                    String.valueOf(tickEpochSecond(currentPrice.getAt())),
                    String.valueOf(nowMillis),
                    String.valueOf(CURRENT_PRICE_TTL.toMillis()),
                    String.valueOf(PRICE_VERSION_TTL.toMillis())
            });
        }

        // 같은 종목은 같은 slot이라 한 노드 연결에서 보낸 순서대로 실행된다.
        List<RedisFuture<String>> futures = marketRedisPipeline.submit(commands -> {
            List<RedisFuture<String>> submitted = new ArrayList<>(keys.size());
            for (int i = 0; i < keys.size(); i++) {
                submitted.add(commands.evalsha(SAVE_IF_NEWER_SHA, ScriptOutputType.VALUE, keys.get(i), args.get(i)));
            }
            return submitted;
        });

        List<OptionalLong> results = new ArrayList<>(Collections.nCopies(currentPrices.size(), OptionalLong.empty()));
        List<Integer> missingScript = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.set(i, toVersion(await(futures.get(i))));
            } catch (RedisNoScriptException ex) {
                missingScript.add(i);
            }
        }
        if (!missingScript.isEmpty()) {
            // 페일오버 등으로 노드에 스크립트가 없으면 본문으로 다시 보낸다. 이후로는 캐시된다.
            List<RedisFuture<String>> retried = marketRedisPipeline.submit(commands -> {
                List<RedisFuture<String>> submitted = new ArrayList<>(missingScript.size());
                for (int index : missingScript) {
                    submitted.add(commands.eval(SAVE_IF_NEWER_LUA, ScriptOutputType.VALUE, keys.get(index), args.get(index)));
                }
                return submitted;
            });
            for (int i = 0; i < missingScript.size(); i++) {
                results.set(missingScript.get(i), toVersion(await(retried.get(i))));
            }
        }

        for (int i = 0; i < currentPrices.size(); i++) {
            OptionalLong version = results.get(i);
            if (version.isPresent()) {
                putLocalCache(currentPrices.get(i).withPriceVersion(version.getAsLong()));
            }
        }
        return results;
    }

    private String serialize(CurrentPrice currentPrice) {
        try {
            return objectMapper.writeValueAsString(currentPrice);
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to serialize current price", ex);
        }
    }

    private static OptionalLong toVersion(String assigned) {
        if (assigned == null || assigned.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(assigned));
    }

    private static String await(RedisFuture<String> future) {
        try {
            return future.get(PIPELINE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while saving current prices", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RedisNoScriptException noScript) {
                throw noScript;
            }
            throw new IllegalStateException("Failed to save current price", ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timed out saving current price", ex);
        }
    }

    @Override
    public void deleteCurrentPrice(Long stockId) {
        redisTemplate.delete(keyForStock(stockId));
        redisTemplate.delete(keyForUpdatedAt(stockId));
        localCache.remove(stockId);
    }

    @Override
    public List<CurrentPrice> findByStockIds(List<Long> stockIds) {
        if (stockIds == null || stockIds.isEmpty()) {
            return List.of();
        }

        List<CurrentPrice> cachedPrices = stockIds.stream()
                .map(this::getFromLocalCache)
                .filter(Objects::nonNull)
                .toList();

        if (cachedPrices.size() == stockIds.size()) {
            return cachedPrices;
        }

        List<Long> cacheMissStockIds = stockIds.stream()
                .filter(stockId -> getFromLocalCache(stockId) == null)
                .toList();

        List<String> keys = cacheMissStockIds.stream()
                .map(this::keyForStock)
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return cachedPrices;
        }

        List<CurrentPrice> redisPrices = values.stream()
                .filter(Objects::nonNull)
                .flatMap(this::deserializeSafely)
                .toList();

        redisPrices.forEach(this::putLocalCache);

        return Stream.concat(cachedPrices.stream(), redisPrices.stream())
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
                LocalDateTime updatedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), MARKET_ZONE);
                result.put(stockIds.get(i), updatedAt);
            } catch (NumberFormatException ex) {
                // 잘못된 값은 skip
            }
        }

        return result;
    }

    private String keyForStock(Long stockId) {
        return KEY_PREFIX + "{stock:" + stockId + "}";
    }

    private String keyForUpdatedAt(Long stockId) {
        return UPDATED_AT_KEY_PREFIX + "{stock:" + stockId + "}";
    }

    private String keyForVersion(Long stockId) {
        return VERSION_KEY_PREFIX + "{stock:" + stockId + "}";
    }

    private long tickEpochSecond(LocalDateTime at) {
        LocalDateTime tickAt = at != null ? at : LocalDateTime.now(MARKET_ZONE);
        return tickAt.atZone(MARKET_ZONE).toEpochSecond();
    }

    private Stream<CurrentPrice> deserializeSafely(String value) {
        try {
            return Stream.of(objectMapper.readValue(value, CurrentPrice.class));
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to deserialize current price", ex);
        }
    }

    private CurrentPrice getFromLocalCache(Long stockId) {
        LocalCacheEntry entry = localCache.get(stockId);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            localCache.remove(stockId, entry);
            return null;
        }

        return entry.currentPrice();
    }

    private void putLocalCache(CurrentPrice currentPrice) {
        localCache.put(
                currentPrice.getStockId(),
                new LocalCacheEntry(currentPrice, Instant.now().plus(LOCAL_CACHE_TTL))
        );
    }

    private record LocalCacheEntry(CurrentPrice currentPrice, Instant expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }
}
