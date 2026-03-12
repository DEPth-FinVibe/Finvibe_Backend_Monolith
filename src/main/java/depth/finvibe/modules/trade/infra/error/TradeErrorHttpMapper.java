package depth.finvibe.modules.trade.infra.error;

import depth.finvibe.modules.trade.domain.error.TradeErrorCode;
import depth.finvibe.common.error.DomainErrorCode;
import depth.finvibe.common.infra.error.DomainErrorHttpMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class TradeErrorHttpMapper implements DomainErrorHttpMapper {

    @Override
    public boolean supports(DomainErrorCode code) {
        return code instanceof TradeErrorCode;
    }

    @Override
    public HttpStatusCode toStatus(DomainErrorCode code) {
        TradeErrorCode tradeCode = (TradeErrorCode) code;
        return switch (tradeCode) {
            case TRADE_NOT_FOUND,
                 PORTFOLIO_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case ALREADY_CANCELLED_TRADE,
                 RESERVED_TRADE_ONLY_CANCELLABLE,
                 INVALID_TRADE_TYPE,
                 INVALID_TRADE_ID_FORMAT,
                 CANNOT_CANCEL_NON_RESERVED_TRADE,
                 MARKET_CLOSED,
                 INSUFFICIENT_BALANCE,
                 INSUFFICIENT_HOLDING_AMOUNT,
                 MARKET_PRICE_MISMATCH -> HttpStatus.BAD_REQUEST;

            case CANNOT_CANCEL_BY_OTHER_USER -> HttpStatus.FORBIDDEN;
        };
    }
}
