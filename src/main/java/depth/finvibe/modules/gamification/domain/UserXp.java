package depth.finvibe.modules.gamification.domain;

import java.util.UUID;

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
public class UserXp extends TimeStampedBaseEntity {
    @Id
    private UUID userId;

    private String nickname;

    @Builder.Default
    private Long totalXp = 0L;

    @Builder.Default
    private Long weeklyXp = 0L;

    @Builder.Default
    private Integer level = 1;

    public void addXp(Long amount) {
        this.totalXp += amount;
        this.weeklyXp += amount;
        updateLevel();
    }

    public void resetWeeklyXp() {
        this.weeklyXp = 0L;
    }

    private void updateLevel() {
        // 간단한 레벨 계산 로직 (예: 1000 XP당 1레벨)
        this.level = (int) (this.totalXp / 1000) + 1;
    }

    public static UserXp of(UUID userId, String nickname) {
        return UserXp.builder()
                .userId(userId)
                .nickname(nickname)
                .build();
    }
}
