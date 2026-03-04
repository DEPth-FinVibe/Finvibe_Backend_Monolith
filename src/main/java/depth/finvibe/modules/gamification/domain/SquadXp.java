package depth.finvibe.modules.gamification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Getter
public class SquadXp extends TimeStampedBaseEntity {
    @Id
    private Long squadId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "squad_id")
    private Squad squad;

    @Builder.Default
    private Long totalXp = 0L;

    @Builder.Default
    private Long weeklyXp = 0L;

    @Builder.Default
    private Double weeklyXpChangeRate = 0.0;

    public void addXp(Long amount) {
        this.totalXp += amount;
        this.weeklyXp += amount;
        updateChangeRate();
    }

    public void resetWeeklyXp() {
        this.weeklyXp = 0L;
        updateChangeRate();
    }

    private void updateChangeRate() {
        long previousTotalXp = this.totalXp - this.weeklyXp;
        if (previousTotalXp <= 0L) {
            this.weeklyXpChangeRate = this.weeklyXp > 0L ? 100.0 : 0.0;
            return;
        }

        this.weeklyXpChangeRate = (double) this.weeklyXp / previousTotalXp * 100;
    }
}
