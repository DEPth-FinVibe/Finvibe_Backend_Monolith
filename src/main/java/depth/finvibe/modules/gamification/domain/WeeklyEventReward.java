package depth.finvibe.modules.gamification.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.gamification.domain.enums.WeeklyEventType;
import depth.finvibe.modules.gamification.domain.vo.Reward;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@SuperBuilder
public class WeeklyEventReward {
    @Id
    @Enumerated(EnumType.STRING)
    private WeeklyEventType type;

    @Embedded
    private Reward reward;
}
