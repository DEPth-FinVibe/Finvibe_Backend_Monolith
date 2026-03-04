package depth.finvibe.modules.gamification.application.port.in;

import java.time.Instant;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.enums.MetricEventType;

public interface MetricEventCommandUseCase {

    /**
     * 사용자 메트릭 이벤트를 처리합니다.
     *
     * @param eventType 메트릭 이벤트 타입
     * @param userId 사용자 ID
     * @param delta 증분 값
     * @param occurredAt 이벤트 발생 시각(UTC)
     */
    void updateUserMetricByEventType(MetricEventType eventType, UUID userId, Double delta, Instant occurredAt);
}
