package depth.finvibe.modules.wallet.application.port.in;

import depth.finvibe.common.investment.dto.SignUpEvent;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;

public interface WalletEventUseCase {
    void handleTradeExecutedEvent(TradeExecutedEvent event);

    void handleSignUpEvent(SignUpEvent event);
}
