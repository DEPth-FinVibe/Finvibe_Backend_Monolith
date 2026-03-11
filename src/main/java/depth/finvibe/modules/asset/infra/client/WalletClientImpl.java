package depth.finvibe.modules.asset.infra.client;

import depth.finvibe.modules.asset.application.port.out.WalletClient;
import depth.finvibe.modules.wallet.application.port.in.WalletQueryUseCase;
import depth.finvibe.modules.wallet.dto.WalletDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletClientImpl implements WalletClient {

    private final WalletQueryUseCase walletQueryUseCase;

    @Override
    public WalletDto.WalletResponse getWalletByUserId(UUID userId) {
        return walletQueryUseCase.getWalletByUserId(userId);
    }
}
