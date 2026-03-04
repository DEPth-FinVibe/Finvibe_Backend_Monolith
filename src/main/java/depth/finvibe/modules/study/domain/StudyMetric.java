package depth.finvibe.modules.study.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
public class StudyMetric {
    @Id
    private UUID userId;

    private Long xpEarned;

    private Long timeSpentMinutes;

    private Instant lastPingAt;

    public static StudyMetric of(UUID userId) {
        return StudyMetric.builder()
                .userId(userId)
                .xpEarned(0L)
                .timeSpentMinutes(0L)
                .lastPingAt(null)
                .build();
    }

    public void addTimeSpentMinutes(long minutes) {
        if (timeSpentMinutes == null) {
            timeSpentMinutes = 0L;
        }
        timeSpentMinutes += minutes;
    }

    public void updateLastPingAt(Instant lastPingAt) {
        this.lastPingAt = lastPingAt;
    }
}
