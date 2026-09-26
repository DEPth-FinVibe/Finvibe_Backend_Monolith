package depth.finvibe.modules.market.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RedisReservationPriceIndexTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ReservationRepositoryImpl reservationRepository;

    @Mock
    private MarketRedisPipeline marketRedisPipeline;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RedisReservationPriceIndex index;

    @BeforeEach
    void setUp() {
        index = new RedisReservationPriceIndex(redisTemplate, reservationRepository, marketRedisPipeline, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("매수는 최고 목표가 이하, 매도는 최저 목표가 이상일 때만 조회가 필요하다고 판단한다")
    void mayTrigger_usesBounds() {
        // given: 매수 목표가 최고 70,000, 매도 목표가 최저 80,000
        givenBounds(1L, 70_000.0, 80_000.0);

        // when
        index.refresh(1L);

        // then
        assertThat(index.mayTrigger(1L, 70_000)).isTrue();
        assertThat(index.mayTrigger(1L, 69_000)).isTrue();
        assertThat(index.mayTrigger(1L, 75_000)).isFalse();
        assertThat(index.mayTrigger(1L, 80_000)).isTrue();
        assertThat(index.mayTrigger(1L, 81_000)).isTrue();
    }

    @Test
    @DisplayName("예약이 없는 종목은 조회하지 않는다")
    void mayTrigger_noReservation_false() {
        assertThat(index.mayTrigger(99L, 1)).isFalse();
    }

    @Test
    @DisplayName("다시 읽었을 때 예약이 모두 없어졌으면 종목을 인덱스에서 뺀다")
    void refresh_emptied_removesStock() {
        // given
        givenBounds(1L, 70_000.0, null);
        index.refresh(1L);
        givenBounds(1L, null, null);

        // when
        index.refresh(1L);

        // then
        assertThat(index.mayTrigger(1L, 1)).isFalse();
    }

    @Test
    @DisplayName("전체 다시 읽기는 예약 종목 집합에 없는 종목을 인덱스에서 뺀다")
    void reloadAll_removesStaleStocks() {
        // given
        givenBounds(1L, 70_000.0, null);
        index.refresh(1L);
        when(reservationRepository.findReservedStockIds()).thenReturn(List.of());

        // when
        index.reloadAll();

        // then
        assertThat(index.mayTrigger(1L, 1)).isFalse();
    }

    @Test
    @DisplayName("이전 데이터는 한 번만 옮기고, 옮긴 뒤에는 전체 키를 훑지 않는다")
    @SuppressWarnings("unchecked")
    void start_migratesLegacyOnlyOnce() {
        // given
        SetOperations<String, String> setOperations = org.mockito.Mockito.mock(SetOperations.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.hasKey(RedisReservationPriceIndex.MIGRATED_KEY)).thenReturn(false, true);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(reservationRepository.findReservedStockIdsByKeyScan()).thenReturn(List.of(3L, 5L));
        when(reservationRepository.findReservedStockIds()).thenReturn(List.of());

        // when
        index.start();
        index.start();

        // then
        verify(setOperations).add(ReservationRepositoryImpl.RESERVED_STOCKS_KEY, "3", "5");
        verify(valueOperations).set(RedisReservationPriceIndex.MIGRATED_KEY, "1");
        verify(reservationRepository, org.mockito.Mockito.times(1)).findReservedStockIdsByKeyScan();
    }

    private void givenBounds(Long stockId, Double maxBuy, Double minSell) {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(eq(ReservationRepositoryImpl.keyForBuyIndex(stockId)), anyLong(), anyLong()))
                .thenReturn(tuples(maxBuy));
        when(zSetOperations.rangeWithScores(eq(ReservationRepositoryImpl.keyForSellIndex(stockId)), anyLong(), anyLong()))
                .thenReturn(tuples(minSell));
    }

    private static Set<ZSetOperations.TypedTuple<String>> tuples(Double score) {
        Set<ZSetOperations.TypedTuple<String>> result = new LinkedHashSet<>();
        if (score != null) {
            result.add(new DefaultTypedTuple<>("trade", score));
        }
        return result;
    }
}
