package depth.finvibe.modules.trade.domain.error;

import depth.finvibe.common.investment.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TradeErrorCode implements DomainErrorCode {

    ALREADY_CANCELLED_TRADE("ALREADY_CANCELLED_TRADE", "이미 취소된 거래입니다."),
    TRADE_NOT_FOUND("TRADE_NOT_FOUND", "거래를 찾을 수 없습니다."),
    RESERVED_TRADE_ONLY_CANCELLABLE("RESERVED_TRADE_ONLY_CANCELLABLE", "예약 상태의 거래만 취소할 수 있습니다."),
    INVALID_TRADE_TYPE("INVALID_TRADE_TYPE", "유효하지 않은 거래 유형입니다."),
    INVALID_TRADE_ID_FORMAT("INVALID_TRADE_ID_FORMAT", "거래 ID 형식이 올바르지 않습니다."),
    CANNOT_CANCEL_NON_RESERVED_TRADE("CANNOT_CANCEL_NON_RESERVED_TRADE", "예약 상태가 아닌 거래는 취소할 수 없습니다."),
    PORTFOLIO_NOT_FOUND("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다."),
    MARKET_CLOSED("MARKET_CLOSED", "시장이 닫혀 있어 거래를 처리할 수 없습니다."),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "잔액이 부족하여 거래를 처리할 수 없습니다."),
    INSUFFICIENT_HOLDING_AMOUNT("INSUFFICIENT_HOLDING_AMOUNT", "보유 수량이 부족하여 거래를 처리할 수 없습니다."),
    CANNOT_CANCEL_BY_OTHER_USER("CANNOT_CANCEL_BY_OTHER_USER", "다른 사용자가 거래를 취소할 수 없습니다."),
    MARKET_PRICE_MISMATCH("MARKET_PRICE_MISMATCH", "시장가와 주문 가격이 일치하지 않습니다.");


    private final String code;
    private final String message;
}
