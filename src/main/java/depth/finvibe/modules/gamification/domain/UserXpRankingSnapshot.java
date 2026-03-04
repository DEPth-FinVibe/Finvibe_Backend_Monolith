package depth.finvibe.modules.gamification.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.gamification.domain.enums.RankingPeriod;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;

@Entity
@Table(
        name = "user_xp_ranking_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_xp_ranking_snapshot_period_user",
                        columnNames = {"periodType", "periodStartDate", "userId"})
        },
        indexes = {
                @Index(
                        name = "idx_user_xp_ranking_snapshot_lookup",
                        columnList = "periodType, periodStartDate, ranking")
        })
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Getter
public class UserXpRankingSnapshot extends TimeStampedBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RankingPeriod periodType;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    private UUID userId;

    private String nickname;

    private Integer ranking;

    @Builder.Default
    private Long currentTotalXp = 0L;

    @Builder.Default
    private Long periodXp = 0L;

    @Builder.Default
    private Long previousPeriodXp = 0L;

    private Double growthRate;

    private LocalDateTime snapshotAt;

    public static UserXpRankingSnapshot of(
            RankingPeriod periodType,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            UUID userId,
            String nickname,
            Integer ranking,
            Long currentTotalXp,
            Long periodXp,
            Long previousPeriodXp,
            Double growthRate,
            LocalDateTime snapshotAt) {
        return UserXpRankingSnapshot.builder()
                .periodType(periodType)
                .periodStartDate(periodStartDate)
                .periodEndDate(periodEndDate)
                .userId(userId)
                .nickname(nickname)
                .ranking(ranking)
                .currentTotalXp(currentTotalXp)
                .periodXp(periodXp)
                .previousPeriodXp(previousPeriodXp)
                .growthRate(growthRate)
                .snapshotAt(snapshotAt)
                .build();
    }
}
