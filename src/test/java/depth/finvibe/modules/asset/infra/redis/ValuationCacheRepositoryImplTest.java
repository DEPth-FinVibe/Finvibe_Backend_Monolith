package depth.finvibe.modules.asset.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioSnapshot;

@ExtendWith(MockitoExtension.class)
class ValuationCacheRepositoryImplTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ValuationCacheRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        repository = new ValuationCacheRepositoryImpl(redisTemplate);
    }

    @Test
    void treatsMissingRequiredFieldAsCacheMiss() {
        when(hashOperations.entries("pf:10")).thenReturn(Map.of(
            "pv", "1000", "cv", "1050", "ac", "3", "ua", "2026-08-29T10:00:00Z"
        ));

        assertThat(repository.findPortfolios(List.of(10L))).isEmpty();
    }

    @Test
    void treatsMalformedUserUpdatedAtAsCacheMiss() {
        when(hashOperations.entries("usr:1")).thenReturn(Map.of(
            "pv", "1000", "cv", "1050", "pr", "5.0", "pc", "2", "ua", "not-an-instant"
        ));

        assertThat(repository.findUser("1")).isEmpty();
    }

    @Test
    void readsValidValuesAndSkipsDeletedPortfolio() {
        when(hashOperations.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (key.equals("pf:10")) {
                return Map.of(
                    "pv", "1000", "cv", "1050", "pr", "5.0", "ac", "3",
                    "ua", "2026-08-29T10:00:00Z", "del", "0"
                );
            }
            return Map.of("del", "1");
        });

        var result = repository.findPortfolios(List.of(10L, 20L));

        assertThat(result).containsOnlyKeys(10L);
        assertThat(result.get(10L).profitRate()).isEqualTo(5.0);
    }

    @Test
    void doesNotRefillWhenDatabaseSnapshotIsOlder() {
        when(hashOperations.entries("pf:10")).thenReturn(Map.of(
            "ua", "2026-08-29T10:00:01Z", "del", "0"
        ));
        PortfolioSnapshot older = new PortfolioSnapshot(
            10L, 1000L, 1050L, 5.0, 3L, Instant.parse("2026-08-29T10:00:00Z")
        );

        repository.cachePortfolioIfNewer(older);

        verify(redisTemplate, never()).execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.<Object[]>any()
        );
    }
}
