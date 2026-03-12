package depth.finvibe.common.infra.error;

import depth.finvibe.common.error.DomainErrorCode;
import org.springframework.http.HttpStatusCode;

/**
 * 도메인 에러 코드를 HTTP 상태 코드로 매핑하기 위한 공통 계약입니다.
 */
public interface DomainErrorHttpMapper {

	boolean supports(DomainErrorCode code);

	HttpStatusCode toStatus(DomainErrorCode code);
}
