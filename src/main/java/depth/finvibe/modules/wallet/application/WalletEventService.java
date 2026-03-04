package depth.finvibe.modules.wallet.application;

import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.wallet.application.port.in.WalletCommandUseCase;
import depth.finvibe.modules.wallet.application.port.in.WalletEventUseCase;
import depth.finvibe.common.investment.dto.SignUpEvent;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletEventService implements WalletEventUseCase {
    private final WalletCommandUseCase commandUseCase;

    @Transactional
    public void handleTradeExecutedEvent(TradeExecutedEvent event) {
        UUID userId = UUID.fromString(event.getUserId());

        if (event.getType().equals("BUY")) {
            commandUseCase.withdraw(userId, event.getPrice() * event.getAmount().longValue());
        } else if (event.getType().equals("SELL")) {
            commandUseCase.deposit(userId, event.getPrice() * event.getAmount().longValue());
        } else {
            log.warn("Ignoring trade event of type: {}", event.getType());
        }
    }

    @Transactional
    public void handleSignUpEvent(SignUpEvent event) {
        UUID userId = UUID.fromString(event.getUserId());
        commandUseCase.createWallet(userId);
    }
}
