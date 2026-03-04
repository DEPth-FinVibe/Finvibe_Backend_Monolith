package depth.finvibe.modules.wallet.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.wallet.domain.Wallet;

public class WalletDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "WalletResponse", description = "지갑 잔액 응답")
    public static class WalletResponse {
        @Schema(description = "지갑 ID", example = "1")
        private Long walletId;
        @Schema(description = "사용자 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID userId;
        @Schema(description = "잔액", example = "100000")
        private Long balance;

        public static WalletResponse from(Wallet wallet) {
            return WalletResponse.builder()
                    .walletId(wallet.getId())
                    .userId(wallet.getUserId())
                    .balance(wallet.getBalance().getPrice())
                    .build();
        }
    }
}
