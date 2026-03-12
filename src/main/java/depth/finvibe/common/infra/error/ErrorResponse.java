package depth.finvibe.common.infra.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ErrorResponse {

	private int status;
	private String code;
	private String message;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<FieldErrorResponse> fieldErrors;

	public static ErrorResponse of(int status, String code, String message) {
		return ErrorResponse.builder()
			.status(status)
			.code(code)
			.message(message)
			.build();
	}

	public static ErrorResponse of(
		int status,
		String code,
		String message,
		List<FieldErrorResponse> fieldErrors
	) {
		return ErrorResponse.builder()
			.status(status)
			.code(code)
			.message(message)
			.fieldErrors(fieldErrors)
			.build();
	}
}
