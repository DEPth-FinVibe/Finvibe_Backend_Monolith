package depth.finvibe.modules.user.domain.error;

import depth.finvibe.common.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 유저(User) 모듈 내에서 발생할 수 있는 비즈니스 에러 정의입니다.
 * 
 * <p>
 * {@link DomainErrorCode}를 구현하여 유저 도메인의 고유한 에러 상황을 명세합니다.
 * 각 상수는 에러 식별 코드와 사용자 메시지를 포함합니다.
 * </p>
 */
@AllArgsConstructor
@Getter
public enum UserErrorCode implements DomainErrorCode {
  INVALID_PHONE_NUMBER_PARAMS("INVALID_PHONE_NUMBER_PARAMS", "휴대폰 번호 형식이 올바르지 않습니다."),
  INVALID_EMAIL_FORMAT("INVALID_EMAIL_FORMAT", "이메일 형식이 올바르지 않습니다."),
  INVALID_LOGIN_ID_FORMAT("INVALID_LOGIN_ID_FORMAT", "로그인 ID 형식이 올바르지 않습니다."),
  EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "이미 존재하는 이메일입니다."),
  LOGIN_ID_ALREADY_EXISTS("LOGIN_ID_ALREADY_EXISTS", "이미 존재하는 로그인 ID입니다."),
  NICKNAME_ALREADY_EXISTS("NICKNAME_ALREADY_EXISTS", "이미 존재하는 닉네임입니다."),
  USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
  INVALID_PASSWORD("INVALID_PASSWORD", "비밀번호가 올바르지 않습니다."),
  USER_DELETED("USER_DELETED", "삭제된 사용자입니다."),
  INTEREST_STOCK_NOT_FOUND("INTEREST_STOCK_NOT_FOUND", "관심 종목을 찾을 수 없습니다."),
  INTEREST_STOCK_ALREADY_EXISTS("INTEREST_STOCK_ALREADY_EXISTS", "이미 등록된 관심 종목입니다."),
  MARKET_DATA_NOT_FOUND("MARKET_DATA_NOT_FOUND", "시장 데이터를 찾을 수 없습니다."),
  GAMIFICATION_DATA_NOT_FOUND("GAMIFICATION_DATA_NOT_FOUND", "게이미피케이션 요약 정보를 찾을 수 없습니다."),
  UNAUTHORIZED_USER_UPDATE("UNAUTHORIZED_USER_UPDATE", "사용자 정보를 수정할 권한이 없습니다."),
  UNAUTHORIZED_INTEREST_STOCK_DELETION("UNAUTHORIZED_INTEREST_STOCK_DELETION", "관심 종목을 삭제할 권한이 없습니다."),
  UNAUTHORIZED_INTEREST_STOCK_CREATION("UNAUTHORIZED_INTEREST_STOCK_CREATION", "관심 종목을 등록할 권한이 없습니다."),
  INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다."),
  INVALID_TEMPORARY_TOKEN("INVALID_TEMPORARY_TOKEN", "임시 토큰이 유효하지 않습니다."),
  EMAIL_MISMATCH("EMAIL_MISMATCH", "외부 로그인 계정과 이메일이 일치하지 않습니다."),
  INVALID_BIRTH_DATE("INVALID_BIRTH_DATE", "유효하지 않은 생년월일입니다."),
  INVALID_NICKNAME_FORMAT("INVALID_NICKNAME_FORMAT", "닉네임 형식이 올바르지 않습니다."),
  INVALID_NAME_FORMAT("INVALID_NAME_FORMAT", "이름 형식이 올바르지 않습니다.");

  private final String code;
  private final String message;
}
