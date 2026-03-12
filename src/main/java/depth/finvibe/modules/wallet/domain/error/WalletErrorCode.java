package depth.finvibe.modules.wallet.domain.error;

import depth.finvibe.common.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 지갑(Wallet) 모듈 내에서 발생할 수 있는 비즈니스 에러 정의입니다.
 * 
 * <p>
 * {@link DomainErrorCode}를 구현하여 지갑 도메인의 고유한 에러 상황을 명세합니다.
 * 각 상수는 에러 식별 코드와 사용자 메시지를 포함합니다.
 * </p>
 */
@AllArgsConstructor
@Getter
public enum WalletErrorCode implements DomainErrorCode {
  INVALID_USER_ID("WALLET_INVALID_USER_ID", "유효하지 않은 사용자 ID입니다."),
  WALLET_NOT_FOUND("WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다."),
  INSUFFICIENT_BALANCE("WALLET_INSUFFICIENT_BALANCE", "잔액이 부족합니다."),
  INVALID_MONEY_PRICE("WALLET_INVALID_MONEY_PRICE", "유효하지 않은 금액입니다.");

  private final String code;
  private final String message;
}
