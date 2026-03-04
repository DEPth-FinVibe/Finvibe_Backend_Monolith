package depth.finvibe.modules.gamification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;
import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Getter
public class SquadRankingHistory extends TimeStampedBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long squadId;

    private Integer ranking;

    private Long totalXp;

    private LocalDate recordDate; // 기록 기준 날짜 (예: 매주 월요일)

    public static SquadRankingHistory of(Long squadId, Integer ranking, Long totalXp) {
        return SquadRankingHistory.builder()
                .squadId(squadId)
                .ranking(ranking)
                .totalXp(totalXp)
                .recordDate(LocalDate.now())
                .build();
    }
}
