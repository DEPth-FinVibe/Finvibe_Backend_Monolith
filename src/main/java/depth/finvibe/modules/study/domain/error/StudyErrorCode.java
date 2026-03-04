package depth.finvibe.modules.study.domain.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

import depth.finvibe.common.gamification.error.DomainErrorCode;

@AllArgsConstructor
@Getter
public enum StudyErrorCode implements DomainErrorCode {
    PING_TOO_FREQUENT("STUDY_PING_TOO_FREQUENT", "1분 이내에는 다시 요청할 수 없습니다.");

    private final String code;
    private final String message;
}
