package depth.finvibe.modules.wallet.application.port.in;


import depth.finvibe.modules.wallet.dto.WalletDto;

import java.util.UUID;

public interface WalletQueryUseCase {
    WalletDto.WalletResponse getWalletByUserId(UUID userId);
}
