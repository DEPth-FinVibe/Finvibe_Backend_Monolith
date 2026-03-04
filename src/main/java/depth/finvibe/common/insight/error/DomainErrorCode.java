package depth.finvibe.common.insight.error;

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
