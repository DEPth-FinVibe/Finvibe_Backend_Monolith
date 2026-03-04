package depth.finvibe.modules.gamification.infra.error;

import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.gamification.error.DomainErrorCode;
import depth.finvibe.common.gamification.infra.error.DomainErrorHttpMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class GamificationErrorHttpMapper implements DomainErrorHttpMapper {
    @Override
    public boolean supports(DomainErrorCode code) {
        return code instanceof GamificationErrorCode;
    }

    @Override
    public HttpStatusCode toStatus(DomainErrorCode code) {
        GamificationErrorCode gamificationErrorCode = (GamificationErrorCode) code;
        return switch (gamificationErrorCode) {
            case SQUAD_NOT_FOUND, USER_SQUAD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN_ACCESS -> HttpStatus.FORBIDDEN;
            case BADGE_ALREADY_EXIST -> HttpStatus.CONFLICT;
            case INVALID_XP_VALUE,
                 INVALID_XP_REASON,
                 INVALID_PERIOD_START_DATE_OR_END_DATE,
                 INVALID_PERIOD_START_DATE_IS_GREATER_THAN_END_DATE,
                 INVALID_METRIC_TYPE,
                 INVALID_METRIC_DELTA,
                 INVALID_TARGET_VALUE,
                 INVALID_REWARD_XP,
                 INVALID_REWARD_BADGE,
                 INVALID_PERSONAL_CHALLENGE_TITLE_IS_EMPTY,
                 INVALID_PERSONAL_CHALLENGE_ID,
                 INVALID_USER_ID,
                 INVALID_PERIOD,
                 INVALID_REWARD,
                 SQUAD_NAME_IS_EMPTY,
                 SQUAD_REGION_IS_EMPTY -> HttpStatus.BAD_REQUEST;
        };
    }
}
