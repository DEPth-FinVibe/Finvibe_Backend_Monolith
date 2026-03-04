package depth.finvibe.common.investment.error;

/**
 * 도메인 계층의 비즈니스 규칙 위반 상황을 정의하기 위한 인터페이스입니다.
 * 
 * <p>
 * 이 인터페이스는 각 모듈(Bounded Context)에서 발생하는 에러를 표준화된 방식으로 정의하기 위한 계약(Contract) 역할을
 * 합니다.
 * 각 모듈은 이 인터페이스를 구현하여 해당 모듈만의 구체적인 에러 코드를 정의해야 합니다.
 * </p>
 */
public interface DomainErrorCode {
  /**
   * 에러를 식별하기 위한 고유 코드를 반환합니다.
   * 
   * @return 에러 코드 (예: 'WALLET_INSUFFICIENT_BALANCE')
   */
  String getCode();

  /**
   * 사용자에게 노출할 에러 메시지를 반환합니다.
   *
   * @return 사용자 메시지
   */
  String getMessage();
}
