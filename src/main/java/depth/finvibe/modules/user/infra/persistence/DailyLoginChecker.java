package depth.finvibe.modules.user.infra.persistence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 일일 로그인 체크 서비스 (출석 도장 로직)
 * Redis를 사용하여 사용자의 당일 로그인 여부를 체크하고 기록합니다.
 * 자정이 지나면 키가 자동으로 만료되어 새로운 날의 로그인이 가능합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLoginChecker implements depth.finvibe.modules.user.application.port.out.DailyLoginChecker {

    private final StringRedisTemplate redisTemplate;
    private static final String LOGIN_KEY_PREFIX = "user:login:";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    /**
     * 사용자의 당일 로그인 여부를 확인하고, 처음이면 기록합니다.
     * TTL은 오늘 자정까지로 설정되어 자정이 지나면 자동으로 키가 삭제됩니다.
     *
     * @param userId 사용자 ID
     * @return true: 당일 첫 로그인, false: 이미 로그인한 기록 있음
     */
    @Override
    public boolean checkAndMarkDailyLogin(UUID userId) {
        LocalDate today = LocalDate.now(ZONE_ID);
        String key = LOGIN_KEY_PREFIX + userId + ":" + today;

        // 오늘 자정까지 남은 시간 계산 (초 단위)
        long secondsUntilMidnight = calculateSecondsUntilMidnight();

        // Redis SETNX (SET if Not eXists) 사용
        // 키가 없으면 설정하고 true 반환, 이미 있으면 false 반환
        Boolean isFirstLogin = redisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                secondsUntilMidnight,
                TimeUnit.SECONDS
        );

        boolean result = Boolean.TRUE.equals(isFirstLogin);

        if (result) {
            log.info("First login of the day detected for user: {} (TTL: {}s until midnight)", userId, secondsUntilMidnight);
        } else {
            log.debug("User {} already logged in today", userId);
        }

        return result;
    }

    /**
     * 현재 시각부터 다음 날 자정까지 남은 시간을 초 단위로 계산합니다.
     *
     * @return 자정까지 남은 초
     */
    private long calculateSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE_ID);
        LocalDateTime midnight = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIDNIGHT);
        return Duration.between(now, midnight).getSeconds();
    }
}
