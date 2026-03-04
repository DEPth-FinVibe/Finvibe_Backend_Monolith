package depth.finvibe.modules.gamification.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.gamification.domain.vo.Xp;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Getter
public class UserXpAward extends TimeStampedBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID userId;

    @Embedded
    private Xp xp;

    public static UserXpAward of(UUID userId, Xp xp) {
        return UserXpAward.builder()
                .userId(userId)
                .xp(xp)
                .build();
    }
}
