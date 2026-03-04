package depth.finvibe.modules.market.domain.error;

import depth.finvibe.common.investment.error.DomainErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WebSocketErrorCode implements DomainErrorCode {

    WEBSOCKET_CONNECTION_FAILED("WEBSOCKET_CONNECTION_FAILED", "error.websocket.connection_failed"),
    UNAUTHORIZED("WEBSOCKET_UNAUTHORIZED", "error.websocket.unauthorized"),
    SUBSCRIPTION_FAILED("WEBSOCKET_SUBSCRIPTION_FAILED", "error.websocket.subscription_failed"),
    SUBSCRIPTION_LIMIT_EXCEEDED("WEBSOCKET_SUBSCRIPTION_LIMIT_EXCEEDED", "error.websocket.subscription_limit_exceeded")
    ;

    private final String code;
    private final String message;
}
