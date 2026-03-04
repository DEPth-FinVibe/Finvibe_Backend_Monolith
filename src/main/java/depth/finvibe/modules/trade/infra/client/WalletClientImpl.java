package depth.finvibe.modules.trade.infra.client;

import depth.finvibe.modules.trade.application.port.out.WalletClient;
import depth.finvibe.modules.wallet.application.port.in.WalletQueryUseCase;
import depth.finvibe.modules.wallet.dto.WalletDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletClientImpl implements WalletClient {

    private final WalletQueryUseCase walletQueryUseCase;

    @Override
    public Long getWalletBalance(UUID userId) {
        WalletDto.WalletResponse wallet = walletQueryUseCase.getWalletByUserId(userId);
        return wallet.getBalance();
    }
}
