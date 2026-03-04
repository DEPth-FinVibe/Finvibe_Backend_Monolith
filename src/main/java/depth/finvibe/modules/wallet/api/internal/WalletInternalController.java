package depth.finvibe.modules.wallet.api.internal;

import depth.finvibe.modules.wallet.application.port.in.WalletQueryUseCase;
import depth.finvibe.modules.wallet.dto.WalletDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class WalletInternalController {
    private final WalletQueryUseCase queryUseCase;

    @GetMapping("/balance")
    public WalletDto.WalletResponse getBalanceByUserId(
            @RequestParam UUID memberId
    ) {
        return queryUseCase.getWalletByUserId(memberId);
    }
}
