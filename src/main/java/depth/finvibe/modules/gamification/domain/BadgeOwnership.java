package depth.finvibe.modules.gamification.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.gamification.domain.enums.Badge;
import depth.finvibe.modules.gamification.domain.idclass.BadgeOwnershipId;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;

@Entity
@IdClass(BadgeOwnershipId.class)
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@SuperBuilder
public class BadgeOwnership extends TimeStampedBaseEntity {
    @Id
    @Enumerated(value = EnumType.STRING)
    private Badge badge;

    @Id
    private UUID ownerId;
}
