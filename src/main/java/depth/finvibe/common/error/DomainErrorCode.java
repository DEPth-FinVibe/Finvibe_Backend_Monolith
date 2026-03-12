package depth.finvibe.common.error;

/**
 * 도메인 계층의 비즈니스 규칙 위반 상황을 정의하기 위한 공통 인터페이스입니다.
 */
public interface DomainErrorCode {

	String getCode();

	String getMessage();
}
