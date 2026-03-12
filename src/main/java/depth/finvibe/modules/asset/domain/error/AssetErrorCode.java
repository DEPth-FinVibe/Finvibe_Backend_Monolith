package depth.finvibe.modules.asset.domain.error;

import depth.finvibe.common.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AssetErrorCode implements DomainErrorCode {
    // Asset
    ONLY_OWNER_CAN_UNREGISTER_ASSET("ONLY_OWNER_CAN_UNREGISTER_ASSET", "자산 등록 해제는 소유자만 할 수 있습니다."),
    ONLY_OWNER_CAN_REGISTER_ASSET("ONLY_OWNER_CAN_REGISTER_ASSET", "자산 등록은 소유자만 할 수 있습니다."),
    ONLY_OWNER_CAN_VIEW_ASSETS("ONLY_OWNER_CAN_VIEW_ASSETS", "자산 조회는 소유자만 할 수 있습니다."),
    ONLY_OWNER_CAN_TRANSFER_ASSET("ONLY_OWNER_CAN_TRANSFER_ASSET", "자산 이동은 소유자만 할 수 있습니다."),
    CANNOT_SELL_NON_EXISTENT_ASSET("CANNOT_SELL_NON_EXISTENT_ASSET", "존재하지 않는 자산은 매도할 수 없습니다."),
    ASSET_NOT_FOUND("ASSET_NOT_FOUND", "자산을 찾을 수 없습니다."),

    // Portfolio Group
    INVALID_PORTFOLIO_GROUP_PARAMS("INVALID_PORTFOLIO_GROUP_PARAMS", "포트폴리오 그룹 파라미터가 유효하지 않습니다."),
    PORTFOLIO_GROUP_NOT_FOUND("PORTFOLIO_GROUP_NOT_FOUND", "포트폴리오 그룹을 찾을 수 없습니다."),
    CANNOT_MODIFY_DEFAULT_PORTFOLIO_GROUP("CANNOT_MODIFY_DEFAULT_PORTFOLIO_GROUP", "기본 포트폴리오 그룹은 수정할 수 없습니다."),
    CANNOT_DELETE_DEFAULT_PORTFOLIO_GROUP("CANNOT_DELETE_DEFAULT_PORTFOLIO_GROUP", "기본 포트폴리오 그룹은 삭제할 수 없습니다."),
    ONLY_OWNER_CAN_DELETE_PORTFOLIO_GROUP("ONLY_OWNER_CAN_DELETE_PORTFOLIO_GROUP", "포트폴리오 그룹 삭제는 소유자만 할 수 있습니다."),
    DEFAULT_PORTFOLIO_GROUP_NOT_FOUND("DEFAULT_PORTFOLIO_GROUP_NOT_FOUND", "기본 포트폴리오 그룹을 찾을 수 없습니다."),
    DEFAULT_PORTFOLIO_GROUP_ALREADY_EXISTS("DEFAULT_PORTFOLIO_GROUP_ALREADY_EXISTS", "기본 포트폴리오 그룹이 이미 존재합니다."),
    INVALID_PORTFOLIO_CHART_DATE_RANGE("INVALID_PORTFOLIO_CHART_DATE_RANGE", "포트폴리오 차트 조회 기간이 유효하지 않습니다."),
    SAME_PORTFOLIO_GROUP_TRANSFER_NOT_ALLOWED("SAME_PORTFOLIO_GROUP_TRANSFER_NOT_ALLOWED", "동일한 포트폴리오 그룹으로 자산을 이동할 수 없습니다."),

    // Money
    NEGATIVE_MONEY_AMOUNT("NEGATIVE_MONEY_AMOUNT", "금액은 0보다 작을 수 없습니다."),
    INVALID_MONEY_PARAMS("INVALID_MONEY_PARAMS", "금액 파라미터가 유효하지 않습니다."),
    CANNOT_ADD_DIFFERENT_CURRENCIES("CANNOT_ADD_DIFFERENT_CURRENCIES", "서로 다른 통화는 더할 수 없습니다."),
    CANNOT_SUBTRACT_DIFFERENT_CURRENCIES("CANNOT_SUBTRACT_DIFFERENT_CURRENCIES", "서로 다른 통화는 뺄 수 없습니다.");

    private final String code;
    private final String message;
}
