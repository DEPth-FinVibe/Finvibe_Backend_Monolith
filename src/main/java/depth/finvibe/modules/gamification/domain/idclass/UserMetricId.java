package depth.finvibe.modules.gamification.domain.idclass;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class UserMetricId implements Serializable {
    private UserMetricType type;
    private UUID userId;
    private CollectPeriod collectPeriod;
}
