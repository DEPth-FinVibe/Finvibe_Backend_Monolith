package depth.finvibe.common.user.infra.error;

import depth.finvibe.common.user.error.DomainErrorCode;
import org.springframework.http.HttpStatusCode;

/**
 * 모듈별 도메인 에러 코드를 HTTP 상태 코드로 매핑하기 위한 계약입니다.
 */
public interface DomainErrorHttpMapper {

  /**
   * 이 매퍼가 해당 에러 코드를 처리할 수 있는지 여부를 반환합니다.
   */
  boolean supports(DomainErrorCode code);

  /**
   * 도메인 에러 코드에 대응하는 HTTP 상태 코드를 반환합니다.
   */
  HttpStatusCode toStatus(DomainErrorCode code);
}
