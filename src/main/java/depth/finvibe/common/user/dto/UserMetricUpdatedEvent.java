package depth.finvibe.common.user.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * 사용자 메트릭 업데이트 이벤트
 * Gamification 서비스로 전송되는 메트릭 업데이트 이벤트
 */
@Builder
public record UserMetricUpdatedEvent(
        String userId,
        String eventType,  // MetricEventType (LOGIN, STOCK_BOUGHT, etc.)
        Double delta,      // 증가량 또는 현재값 (이벤트 타입에 따라 다름)
        Instant occurredAt // 이벤트 발생 시각
) {
}
