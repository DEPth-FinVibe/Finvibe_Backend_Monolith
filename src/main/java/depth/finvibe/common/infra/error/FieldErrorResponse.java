package depth.finvibe.common.infra.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class FieldErrorResponse {

	private final String field;
	private final String message;
}
