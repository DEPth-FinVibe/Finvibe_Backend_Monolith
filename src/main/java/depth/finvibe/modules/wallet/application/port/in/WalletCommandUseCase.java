package depth.finvibe.modules.wallet.application.port.in;


import depth.finvibe.modules.wallet.dto.WalletDto;

import java.util.UUID;

public interface WalletCommandUseCase {
    WalletDto.WalletResponse createWallet(UUID userId);
    WalletDto.WalletResponse deposit(UUID userId, Long price);
    WalletDto.WalletResponse withdraw(UUID userId, Long price);
}
