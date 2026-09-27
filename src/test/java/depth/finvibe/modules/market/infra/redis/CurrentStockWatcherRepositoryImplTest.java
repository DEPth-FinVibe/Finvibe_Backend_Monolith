package depth.finvibe.modules.market.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;

import depth.finvibe.modules.market.domain.CurrentStockWatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class CurrentStockWatcherRepositoryImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private CurrentStockWatcherRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CurrentStockWatcherRepositoryImpl(redisTemplate);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("감시 종목은 전체 키 검색 없이, 만료된 종목을 지운 뒤 인덱스에서 읽는다")
    void findActiveStockIds_readsIndexWithoutKeys() {
        when(zSetOperations.range(CurrentStockWatcherRepositoryImpl.ACTIVE_INDEX_KEY, 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("3", "x", "5")));

        List<Long> result = repository.findActiveStockIds();

        assertThat(result).containsExactly(3L, 5L);
        verify(zSetOperations).removeRangeByScore(eq(CurrentStockWatcherRepositoryImpl.ACTIVE_INDEX_KEY), eq(Double.NEGATIVE_INFINITY), anyDouble());
        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    @DisplayName("감시를 등록하면 종목을 만료 시각과 함께 인덱스에 넣는다")
    void save_touchesIndex() {
        repository.save(CurrentStockWatcher.create(7L, 1L));

        verify(zSetOperations).add(eq(CurrentStockWatcherRepositoryImpl.ACTIVE_INDEX_KEY), eq("7"), anyDouble());
    }

    @Test
    @DisplayName("마지막 감시자가 빠지면 인덱스에서 종목을 뺀다")
    void remove_lastWatcher_removesFromIndex() {
        when(setOperations.size(anyString())).thenReturn(0L);

        repository.remove(CurrentStockWatcher.create(7L, 1L));

        verify(zSetOperations).remove(CurrentStockWatcherRepositoryImpl.ACTIVE_INDEX_KEY, "7");
    }
}
