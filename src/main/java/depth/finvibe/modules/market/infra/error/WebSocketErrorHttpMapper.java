package depth.finvibe.modules.market.infra.error;

import depth.finvibe.modules.market.domain.error.WebSocketErrorCode;
import depth.finvibe.common.error.DomainErrorCode;
import depth.finvibe.common.infra.error.DomainErrorHttpMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class WebSocketErrorHttpMapper implements DomainErrorHttpMapper {

    @Override
    public boolean supports(DomainErrorCode code) {
        return code instanceof WebSocketErrorCode;
    }

    @Override
    public HttpStatusCode toStatus(DomainErrorCode code) {
        WebSocketErrorCode wsCode = (WebSocketErrorCode) code;
        return switch (wsCode) {
            case WEBSOCKET_CONNECTION_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case SUBSCRIPTION_FAILED, SUBSCRIPTION_LIMIT_EXCEEDED -> HttpStatus.BAD_REQUEST;
        };
    }
}
