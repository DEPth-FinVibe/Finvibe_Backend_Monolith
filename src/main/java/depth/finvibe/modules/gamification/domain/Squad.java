package depth.finvibe.modules.gamification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.domain.TimeStampedBaseEntity;
import depth.finvibe.common.error.DomainException;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Getter
public class Squad extends TimeStampedBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String region; // 예: 서울, 경기 등

    public void updateInfo(String name, String region) {
        validateName(name);
        validateRegion(region);

        this.name = name;
        this.region = region;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(GamificationErrorCode.SQUAD_NAME_IS_EMPTY);
        }
    }

    private void validateRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new DomainException(GamificationErrorCode.SQUAD_REGION_IS_EMPTY);
        }
    }
}
