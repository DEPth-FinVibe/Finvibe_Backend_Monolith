package depth.finvibe.modules.study.infra.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.study.domain.error.StudyErrorCode;
import depth.finvibe.common.gamification.error.DomainErrorCode;
import depth.finvibe.common.gamification.infra.error.DomainErrorHttpMapper;

@Component
public class StudyErrorHttpMapper implements DomainErrorHttpMapper {
    @Override
    public boolean supports(DomainErrorCode code) {
        return code instanceof StudyErrorCode;
    }

    @Override
    public HttpStatusCode toStatus(DomainErrorCode code) {
        if (code == StudyErrorCode.PING_TOO_FREQUENT) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
