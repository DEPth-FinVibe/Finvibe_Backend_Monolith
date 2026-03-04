package depth.finvibe.modules.gamification.domain.idclass;

import java.io.Serializable;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.enums.Badge;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class BadgeOwnershipId implements Serializable {
    private Badge badge;

    private UUID ownerId;
}
