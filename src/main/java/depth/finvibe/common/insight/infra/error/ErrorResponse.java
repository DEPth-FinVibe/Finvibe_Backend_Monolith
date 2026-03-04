package depth.finvibe.common.insight.infra.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class ErrorResponse {
  private final int status;
  private final String code;
  private final String message;
}
