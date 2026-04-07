package depth.finvibe.modules.gamification.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.gamification.domain.vo.Xp;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;

@Entity
@Table(
        name = "user_xp_award",
        indexes = {
                @Index(name = "idx_user_xp_award_created_user", columnList = "createdAt,userId"),
                @Index(name = "idx_user_xp_award_user_created", columnList = "userId,createdAt")
        }
)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Getter
public class UserXpAward extends TimeStampedBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
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
