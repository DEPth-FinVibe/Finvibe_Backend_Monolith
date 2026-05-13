package depth.finvibe.common.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioGroupChangedEvent {
    private EventType eventType;
    private String userId; // 추후 정수 기반 UserID로 변경
    private Long portfolioId;
    private Long targetPortfolioId;
    private Instant occurredAt;

    public enum EventType {
        CREATED,
        UPDATED,
        DELETED
    }
}
