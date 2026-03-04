package depth.finvibe.modules.wallet.api.external;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.wallet.application.port.in.WalletQueryUseCase;
import depth.finvibe.modules.wallet.dto.WalletDto;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Tag(name = "지갑", description = "지갑 API")
public class WalletController {
    private final WalletQueryUseCase queryUseCase;

    @GetMapping("/balance")
    @Operation(summary = "지갑 잔액 조회", description = "사용자 지갑 잔액을 조회합니다.")
    public WalletDto.WalletResponse getBalanceByUserId(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester
    ) {
        return queryUseCase.getWalletByUserId(requester.getUuid());
    }
}
