package depth.finvibe.modules.gamification.domain.vo;

import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.springframework.util.StringUtils;

@AllArgsConstructor
@NoArgsConstructor
@Builder(access = AccessLevel.PRIVATE)
@Getter
@Embeddable
public class Xp {

    private Long value;

    private String reason;

    public static Xp of(Long value, String reason) {
        checkArgumentsAreValid(value, reason);

        return Xp.builder()
                .value(value)
                .reason(reason)
                .build();
    }

    private static void checkArgumentsAreValid(Long value, String reason) {
        if (value == null || value <= 0) {
            throw new DomainException(GamificationErrorCode.INVALID_XP_VALUE);
        }

        if (!StringUtils.hasText(reason)) {
            throw new DomainException(GamificationErrorCode.INVALID_XP_REASON);
        }
    }
}
