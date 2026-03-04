package depth.finvibe.common.investment.infra.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import depth.finvibe.common.investment.error.DomainErrorCode;
import depth.finvibe.common.investment.error.GlobalErrorCode;

@Component
public class GlobalErrorHttpMapper implements DomainErrorHttpMapper {

  @Override
  public boolean supports(DomainErrorCode code) {
    return code instanceof GlobalErrorCode;
  }

  @Override
  public HttpStatusCode toStatus(DomainErrorCode code) {
    GlobalErrorCode globalCode = (GlobalErrorCode) code;
    return switch (globalCode) {
      case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
      case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
      case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
      case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
      case CIRCUIT_BREAKER_OPEN -> HttpStatus.SERVICE_UNAVAILABLE;
      case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
