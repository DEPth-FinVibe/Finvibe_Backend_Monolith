package depth.finvibe.common.user.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 도메인 모델 내에서 비즈니스 로직 위반 시 발생하는 최상위 예외 클래스입니다.
 * 
 * <p>
 * 체크 예외(Checked Exception)가 아닌 언체크 예외(RuntimeException)를 상속받습니다.
 * 이는 도메인 규칙 위반이 프로그래밍적으로 복구 불가능한 경우가 많고, 
 * 비즈니스 로직을 오염시키지 않으면서 전역적인 에러 핸들러에서 일괄 처리하기 위함입니다.
 * </p>
 */
@AllArgsConstructor
@Getter
public class DomainException extends RuntimeException {

  /**
   * 발생한 에러의 세부 정보를 담고 있는 도메인 에러 코드입니다.
   */
  private final DomainErrorCode errorCode;

}
