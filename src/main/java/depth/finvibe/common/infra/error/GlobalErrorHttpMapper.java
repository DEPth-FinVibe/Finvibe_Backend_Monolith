package depth.finvibe.common.infra.error;

import depth.finvibe.common.error.DomainErrorCode;
import depth.finvibe.common.error.GlobalErrorCode;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class GlobalErrorHttpMapper implements DomainErrorHttpMapper {

	private static final Set<String> SUPPORTED_CODES = Set.of(
		GlobalErrorCode.INVALID_REQUEST.getCode(),
		GlobalErrorCode.METHOD_NOT_ALLOWED.getCode(),
		GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
		GlobalErrorCode.NOT_ACCEPTABLE.getCode(),
		GlobalErrorCode.NOT_FOUND.getCode(),
		GlobalErrorCode.AUTHENTICATION_FAILED.getCode(),
		GlobalErrorCode.ACCESS_DENIED.getCode(),
		GlobalErrorCode.CIRCUIT_BREAKER_OPEN.getCode(),
		GlobalErrorCode.INTERNAL_SERVER_ERROR.getCode()
	);

	@Override
	public boolean supports(DomainErrorCode code) {
		return SUPPORTED_CODES.contains(code.getCode());
	}

	@Override
	public HttpStatusCode toStatus(DomainErrorCode code) {
		return switch (code.getCode()) {
			case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
			case "METHOD_NOT_ALLOWED" -> HttpStatus.METHOD_NOT_ALLOWED;
			case "UNSUPPORTED_MEDIA_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
			case "NOT_ACCEPTABLE" -> HttpStatus.NOT_ACCEPTABLE;
			case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
			case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
			case "CIRCUIT_BREAKER_OPEN" -> HttpStatus.SERVICE_UNAVAILABLE;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}
}
