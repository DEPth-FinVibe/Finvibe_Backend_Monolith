package depth.finvibe.modules.wallet.application.port.in;


import depth.finvibe.modules.wallet.dto.WalletDto;

import java.util.UUID;

public interface WalletCommandUseCase {
    WalletDto.WalletResponse createWallet(Long userId);
    WalletDto.WalletResponse deposit(Long userId, Long price);
    WalletDto.WalletResponse withdraw(Long userId, Long price);
}
