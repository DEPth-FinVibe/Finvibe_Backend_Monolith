package depth.finvibe.modules.asset.application.port.out;

import depth.finvibe.modules.wallet.dto.WalletDto;
import java.util.UUID;

public interface WalletClient {
    WalletDto.WalletResponse getWalletByUserId(UUID userId);
}
