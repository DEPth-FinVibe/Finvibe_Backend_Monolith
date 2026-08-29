package depth.finvibe.modules.asset.infra.redis;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.ValuationCacheRepository;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioSnapshot;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.UserSnapshot;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ValuationCacheRepositoryImpl implements ValuationCacheRepository {
    private static final DefaultRedisScript<Long> CACHE_PORTFOLIO_IF_UNCHANGED = new DefaultRedisScript<>("""
        local exists = redis.call('EXISTS', KEYS[1])
        local current = redis.call('HGET', KEYS[1], 'ua')
        if exists == 1 then
            if redis.call('HGET', KEYS[1], 'del') == '1' then return 0 end
            if not current or current ~= ARGV[1] then return 0 end
        else
            if ARGV[1] ~= '' then return 0 end
            redis.call('HSET', KEYS[1], 'cvp', ARGV[3])
        end
        redis.call('HSET', KEYS[1],
            'pv', ARGV[2], 'cv', ARGV[3], 'pr', ARGV[4],
            'ac', ARGV[5], 'ua', ARGV[6], 'del', '0')
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CACHE_USER_IF_UNCHANGED = new DefaultRedisScript<>("""
        local exists = redis.call('EXISTS', KEYS[1])
        local current = redis.call('HGET', KEYS[1], 'ua')
        if exists == 1 then
            if not current or current ~= ARGV[1] then return 0 end
        else
            if ARGV[1] ~= '' then return 0 end
            redis.call('HSET', KEYS[1], 'cvp', ARGV[3])
        end
        redis.call('HSET', KEYS[1],
            'pv', ARGV[2], 'cv', ARGV[3], 'pr', ARGV[4],
            'pc', ARGV[5], 'ua', ARGV[6])
        return 1
        """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<UserSnapshot> findUser(String userId) {
        return parseUser(userId, entries(userKey(userId)));
    }

    @Override
    public Map<Long, PortfolioSnapshot> findPortfolios(List<Long> portfolioIds) {
        Map<Long, PortfolioSnapshot> snapshots = new LinkedHashMap<>();
        for (Long portfolioId : portfolioIds) {
            parsePortfolio(portfolioId, entries(portfolioKey(portfolioId)))
                .ifPresent(snapshot -> snapshots.put(portfolioId, snapshot));
        }
        return snapshots;
    }

    @Override
    public void cacheUserIfNewer(UserSnapshot snapshot) {
        if (snapshot.updatedAt() == null) {
            return;
        }
        String key = userKey(snapshot.userId());
        Optional<String> expected = expectedVersionForRefill(entries(key), snapshot.updatedAt(), false);
        expected.ifPresent(version -> redisTemplate.execute(
            CACHE_USER_IF_UNCHANGED,
            List.of(key),
            version,
            String.valueOf(snapshot.purchasedValue()),
            String.valueOf(snapshot.currentValue()),
            String.valueOf(snapshot.profitRate()),
            String.valueOf(snapshot.portfolioCount()),
            snapshot.updatedAt().toString()
        ));
    }

    @Override
    public void cachePortfolioIfNewer(PortfolioSnapshot snapshot) {
        if (snapshot.updatedAt() == null) {
            return;
        }
        String key = portfolioKey(snapshot.portfolioId());
        Map<Object, Object> current = entries(key);
        Optional<String> expected = expectedVersionForRefill(current, snapshot.updatedAt(), true);
        expected.ifPresent(version -> redisTemplate.execute(
            CACHE_PORTFOLIO_IF_UNCHANGED,
            List.of(key),
            version,
            String.valueOf(snapshot.purchasedValue()),
            String.valueOf(snapshot.currentValue()),
            String.valueOf(snapshot.profitRate()),
            String.valueOf(snapshot.assetCount()),
            snapshot.updatedAt().toString()
        ));
    }

    private Optional<UserSnapshot> parseUser(String userId, Map<Object, Object> values) {
        try {
            return Optional.of(new UserSnapshot(
                userId,
                requiredLong(values, "pv"),
                requiredLong(values, "cv"),
                requiredDouble(values, "pr"),
                requiredLong(values, "pc"),
                requiredInstant(values, "ua")
            ));
        } catch (IllegalArgumentException ex) {
            log.debug("User valuation cache miss. userId={}, reason={}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<PortfolioSnapshot> parsePortfolio(Long portfolioId, Map<Object, Object> values) {
        try {
            if ("1".equals(value(values, "del"))) {
                return Optional.empty();
            }
            return Optional.of(new PortfolioSnapshot(
                portfolioId,
                requiredLong(values, "pv"),
                requiredLong(values, "cv"),
                requiredDouble(values, "pr"),
                requiredLong(values, "ac"),
                requiredInstant(values, "ua")
            ));
        } catch (IllegalArgumentException ex) {
            log.debug("Portfolio valuation cache miss. portfolioId={}, reason={}", portfolioId, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> expectedVersionForRefill(
        Map<Object, Object> values,
        Instant databaseUpdatedAt,
        boolean rejectDeleted
    ) {
        if (values.isEmpty()) {
            return Optional.of("");
        }
        if (rejectDeleted && "1".equals(value(values, "del"))) {
            return Optional.empty();
        }
        String currentValue = value(values, "ua");
        if (currentValue == null) {
            return Optional.empty();
        }
        try {
            Instant currentUpdatedAt = Instant.parse(currentValue);
            return databaseUpdatedAt.isAfter(currentUpdatedAt)
                ? Optional.of(currentValue)
                : Optional.empty();
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private Map<Object, Object> entries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    private long requiredLong(Map<Object, Object> values, String field) {
        try {
            return Long.parseLong(required(values, field));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid " + field, ex);
        }
    }

    private double requiredDouble(Map<Object, Object> values, String field) {
        try {
            double parsed = Double.parseDouble(required(values, field));
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException("invalid " + field);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid " + field, ex);
        }
    }

    private Instant requiredInstant(Map<Object, Object> values, String field) {
        try {
            return Instant.parse(required(values, field));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid " + field, ex);
        }
    }

    private String required(Map<Object, Object> values, String field) {
        String value = value(values, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }

    private String value(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }

    private String portfolioKey(Long portfolioId) {
        return "pf:" + portfolioId;
    }

    private String userKey(String userId) {
        return "usr:" + userId;
    }
}
