package depth.finvibe.modules.market.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CandleRefreshVerificationRepositoryImplTest {

    private static final Long STOCK_ID = 4971L;
    private static final String KEY = "market:candle:minute-refresh:{stock:4971}";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CandleRefreshVerificationRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new CandleRefreshVerificationRepositoryImpl(redisTemplate);
    }

    @Test
    @DisplayName("저장된 완료 분이 요청한 완료 분과 같거나 이후이면 검증 완료로 판단한다")
    void isVerified_savedMinuteAtOrAfterRequested_returnsTrue() {
        LocalDateTime requestedMinute = LocalDateTime.of(2026, 8, 21, 10, 0);
        when(valueOperations.get(KEY)).thenReturn(requestedMinute.plusMinutes(1).toString());

        boolean verified = repository.isVerified(STOCK_ID, requestedMinute);

        assertThat(verified).isTrue();
    }

    @Test
    @DisplayName("완료 분 검증 시 종목별 키에 이틀 TTL로 기록한다")
    void markVerified_completedMinute_savesWithTtl() {
        LocalDateTime completedMinute = LocalDateTime.of(2026, 8, 21, 10, 0);

        repository.markVerified(STOCK_ID, completedMinute);

        verify(valueOperations).set(KEY, completedMinute.toString(), Duration.ofDays(2));
    }

    @Test
    @DisplayName("Redis 값이 손상되면 미검증으로 판단해 외부 조회를 허용한다")
    void isVerified_invalidValue_returnsFalse() {
        when(valueOperations.get(KEY)).thenReturn("invalid");

        boolean verified = repository.isVerified(
                STOCK_ID,
                LocalDateTime.of(2026, 8, 21, 10, 0)
        );

        assertThat(verified).isFalse();
    }
}

