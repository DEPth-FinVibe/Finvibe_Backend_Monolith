package depth.finvibe.modules.market.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Set;

import depth.finvibe.modules.market.domain.Reservation;
import depth.finvibe.modules.market.domain.enums.ReservationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ReservationRepositoryImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private ReservationRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ReservationRepositoryImpl(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("예약을 저장하면 예약 종목 집합에 넣고 변경을 알린다")
    void save_addsReservedStockAndNotifies() {
        repository.save(reservation(10L, 7L, ReservationType.BUY));

        verify(zSetOperations).add("market:reservation:buy:stock:7", "10", 70_000.0);
        verify(setOperations).add(ReservationRepositoryImpl.RESERVED_STOCKS_KEY, "7");
        verify(redisTemplate).convertAndSend(ReservationRepositoryImpl.CHANGED_CHANNEL, "7");
    }

    @Test
    @DisplayName("종목의 마지막 예약을 지우면 예약 종목 집합에서 빼고 변경을 알린다")
    void delete_lastReservation_removesStock() {
        when(valueOperations.get("market:reservation:trade:10"))
                .thenReturn(objectMapper.writeValueAsString(reservation(10L, 7L, ReservationType.SELL)));
        when(zSetOperations.zCard(anyString())).thenReturn(0L);

        repository.deleteByTradeId(10L);

        verify(setOperations).remove(ReservationRepositoryImpl.RESERVED_STOCKS_KEY, "7");
        verify(redisTemplate).convertAndSend(ReservationRepositoryImpl.CHANGED_CHANNEL, "7");
    }

    @Test
    @DisplayName("다른 예약이 남아 있으면 예약 종목 집합에서 빼지 않는다")
    void delete_remainingReservation_keepsStock() {
        when(valueOperations.get("market:reservation:trade:10"))
                .thenReturn(objectMapper.writeValueAsString(reservation(10L, 7L, ReservationType.BUY)));
        when(zSetOperations.zCard("market:reservation:buy:stock:7")).thenReturn(1L);

        repository.deleteByTradeId(10L);

        verify(setOperations, never()).remove(anyString(), any());
    }

    @Test
    @DisplayName("예약 종목은 전체 키 검색 없이 집합에서 읽는다")
    void findReservedStockIds_readsSet() {
        when(setOperations.members(ReservationRepositoryImpl.RESERVED_STOCKS_KEY)).thenReturn(Set.of("5", "3", "x"));

        assertThat(repository.findReservedStockIds()).containsExactly(3L, 5L);
        verify(redisTemplate, never()).keys(anyString());
    }

    private static Reservation reservation(Long tradeId, Long stockId, ReservationType type) {
        return Reservation.create(tradeId, stockId, 70_000L, type, LocalDateTime.parse("2026-09-26T10:00:00"));
    }
}
