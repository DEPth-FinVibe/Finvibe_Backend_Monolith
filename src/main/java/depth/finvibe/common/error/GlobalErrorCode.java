package depth.finvibe.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements DomainErrorCode {
	INVALID_REQUEST("INVALID_REQUEST", "잘못된 요청입니다."),
	METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "허용되지 않은 메서드입니다."),
	UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 미디어 타입입니다."),
	NOT_ACCEPTABLE("NOT_ACCEPTABLE", "요청한 응답 형식을 제공할 수 없습니다."),
	NOT_FOUND("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
	AUTHENTICATION_FAILED("UNAUTHORIZED", "인증에 실패했습니다."),
	ACCESS_DENIED("FORBIDDEN", "접근 권한이 없습니다."),
	CIRCUIT_BREAKER_OPEN("CIRCUIT_BREAKER_OPEN", "외부 API 서비스가 일시적으로 사용 불가능합니다."),
	INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

	private final String code;
	private final String message;

	public static GlobalErrorCode fromStatus(HttpStatusCode status) {
		int value = status.value();
		if (value == HttpStatus.BAD_REQUEST.value()) {
			return INVALID_REQUEST;
		}
		if (value == HttpStatus.METHOD_NOT_ALLOWED.value()) {
			return METHOD_NOT_ALLOWED;
		}
		if (value == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
			return UNSUPPORTED_MEDIA_TYPE;
		}
		if (value == HttpStatus.NOT_ACCEPTABLE.value()) {
			return NOT_ACCEPTABLE;
		}
		if (value == HttpStatus.NOT_FOUND.value()) {
			return NOT_FOUND;
		}
		if (value == HttpStatus.UNAUTHORIZED.value()) {
			return AUTHENTICATION_FAILED;
		}
		if (value == HttpStatus.FORBIDDEN.value()) {
			return ACCESS_DENIED;
		}
		if (value == HttpStatus.SERVICE_UNAVAILABLE.value()) {
			return CIRCUIT_BREAKER_OPEN;
		}
		return INTERNAL_SERVER_ERROR;
	}
}
