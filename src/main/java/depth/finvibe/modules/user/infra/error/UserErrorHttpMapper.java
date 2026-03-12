package depth.finvibe.modules.user.infra.error;

import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.common.error.DomainErrorCode;
import depth.finvibe.common.infra.error.DomainErrorHttpMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class UserErrorHttpMapper implements DomainErrorHttpMapper {

  @Override
  public boolean supports(DomainErrorCode code) {
    return code instanceof UserErrorCode;
  }

  @Override
  public HttpStatusCode toStatus(DomainErrorCode code) {
    UserErrorCode userCode = (UserErrorCode) code;
    return switch (userCode) {
      case INVALID_PHONE_NUMBER_PARAMS,
          INVALID_EMAIL_FORMAT,
          INVALID_LOGIN_ID_FORMAT,
          INVALID_BIRTH_DATE,
          INVALID_NICKNAME_FORMAT,
          INVALID_NAME_FORMAT,
          EMAIL_MISMATCH -> HttpStatus.BAD_REQUEST;

      case INVALID_PASSWORD,
          INVALID_REFRESH_TOKEN,
          INVALID_TEMPORARY_TOKEN -> HttpStatus.UNAUTHORIZED;

      case UNAUTHORIZED_USER_UPDATE,
          UNAUTHORIZED_INTEREST_STOCK_DELETION,
          UNAUTHORIZED_INTEREST_STOCK_CREATION,
          USER_DELETED -> HttpStatus.FORBIDDEN;

      case USER_NOT_FOUND,
          INTEREST_STOCK_NOT_FOUND,
          MARKET_DATA_NOT_FOUND,
          GAMIFICATION_DATA_NOT_FOUND -> HttpStatus.NOT_FOUND;

      case EMAIL_ALREADY_EXISTS,
          LOGIN_ID_ALREADY_EXISTS,
          NICKNAME_ALREADY_EXISTS,
          INTEREST_STOCK_ALREADY_EXISTS -> HttpStatus.CONFLICT;
    };
  }
}
