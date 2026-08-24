package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CandleRefreshVerificationRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CandleRefreshVerificationRepositoryImpl implements CandleRefreshVerificationRepository {

    private static final String KEY_PREFIX = "market:candle:minute-refresh:";
    private static final Duration VERIFICATION_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isVerified(Long stockId, LocalDateTime completedMinute) {
        try {
            String verifiedValue = redisTemplate.opsForValue().get(keyForStock(stockId));
            if (verifiedValue == null) {
                return false;
            }
            LocalDateTime verifiedMinute = LocalDateTime.parse(verifiedValue);
            return !verifiedMinute.isBefore(completedMinute);
        } catch (RuntimeException ex) {
            log.warn("Failed to read minute candle refresh verification. stockId={}", stockId, ex);
            return false;
        }
    }

    @Override
    public void markVerified(Long stockId, LocalDateTime completedMinute) {
        try {
            redisTemplate.opsForValue().set(
                    keyForStock(stockId),
                    completedMinute.toString(),
                    VERIFICATION_TTL
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to save minute candle refresh verification. stockId={}", stockId, ex);
        }
    }

    private String keyForStock(Long stockId) {
        return KEY_PREFIX + "{stock:" + stockId + "}";
    }
}
