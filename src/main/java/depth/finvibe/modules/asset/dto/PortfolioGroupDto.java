package depth.finvibe.modules.asset.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.Currency;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.domain.PortfolioValuation;

public class PortfolioGroupDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "PortfolioGroupResponse", description = "포트폴리오 그룹 응답")
    public static class PortfolioGroupResponse {
        @Schema(description = "포트폴리오 그룹 ID", example = "1")
        private Long id;
        @Schema(description = "포트폴리오 그룹 이름", example = "내 포트폴리오")
        private String name;
        @Schema(description = "아이콘 코드", example = "ICON_01")
        private String iconCode;
        @Schema(description = "투자원금", example = "1000000")
        private BigDecimal totalPurchaseAmount;
        @Schema(description = "현재 평가금액", example = "1100000")
        private BigDecimal totalCurrentValue;
        @Schema(description = "수익률(%)", example = "10.00")
        private BigDecimal totalReturnRate;

        public static PortfolioGroupResponse from(PortfolioGroup portfolioGroup) {
            PortfolioValuation valuation = portfolioGroup.getValuation();
            BigDecimal currentValue = BigDecimal.ZERO;
            BigDecimal purchaseAmount = BigDecimal.ZERO;
            BigDecimal returnRate = BigDecimal.ZERO;

            if (valuation != null) {
                currentValue = valuation.getTotalCurrentValue();
                purchaseAmount = currentValue.subtract(valuation.getTotalProfitLoss());
                returnRate = valuation.getTotalReturnRate();
            }

            return PortfolioGroupResponse.builder()
                    .id(portfolioGroup.getId())
                    .name(portfolioGroup.getName())
                    .iconCode(portfolioGroup.getIconCode())
                    .totalPurchaseAmount(purchaseAmount)
                    .totalCurrentValue(currentValue)
                    .totalReturnRate(returnRate)
                    .build();
        }
    }
    
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "CreatePortfolioGroupRequest", description = "포트폴리오 그룹 생성 요청")
    public static class CreatePortfolioGroupRequest {
        @NotNull
        @Schema(description = "포트폴리오 그룹 이름", example = "성장주 포트폴리오")
        private String name;
        @NotNull
        @Schema(description = "아이콘 코드", example = "ICON_02")
        private String iconCode;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "UpdatePortfolioGroupRequest", description = "포트폴리오 그룹 수정 요청")
    public static class UpdatePortfolioGroupRequest {
        @NotNull
        @Schema(description = "포트폴리오 그룹 이름", example = "수정된 포트폴리오")
        private String name;
        @NotNull
        @Schema(description = "아이콘 코드", example = "ICON_03")
        private String iconCode;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "RegisterAssetRequest", description = "자산 등록 요청")
    public static class RegisterAssetRequest {
        @NotNull
        @Schema(description = "종목 ID", example = "101")
        private Long stockId;
        @NotNull
        @Schema(description = "보유 수량", example = "10.5")
        private BigDecimal amount;
        @NotNull
        @Schema(description = "매수 단가", example = "150.25")
        private BigDecimal stockPrice;
        @NotNull
        @Schema(description = "자산 이름", example = "애플")
        private String name;
        @NotNull
        @Schema(description = "통화", example = "USD")
        private Currency currency;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "UnregisterAssetRequest", description = "자산 삭제 요청")
    public static class UnregisterAssetRequest {
        @NotNull
        @Schema(description = "종목 ID", example = "101")
        private Long stockId;
        @NotNull
        @Schema(description = "삭제 수량", example = "2")
        private BigDecimal amount;
        @NotNull
        @Schema(description = "기준 단가", example = "160.00")
        private BigDecimal stockPrice;
        @Schema(description = "통화", example = "USD")
        private Currency currency;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "TransferAssetRequest", description = "자산 이동 요청")
    public static class TransferAssetRequest {
        @NotNull
        @Schema(description = "이동 대상 포트폴리오 그룹 ID", example = "2")
        private Long targetPortfolioId;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "AssetAllocationResponse", description = "전체 자산 배분 응답")
    public static class AssetAllocationResponse {
        @Schema(description = "현금 금액", example = "3500000")
        private BigDecimal cashAmount;
        @Schema(description = "주식 금액(현재 평가금액 기준)", example = "7600000")
        private BigDecimal stockAmount;
        @Schema(description = "총 자산 금액", example = "11100000")
        private BigDecimal totalAmount;
        @Schema(description = "기준금액(10000000) 대비 증감 금액", example = "1100000")
        private BigDecimal changeAmount;
        @Schema(description = "기준금액(10000000) 대비 증감률(%)", example = "11.00")
        private BigDecimal changeRate;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "PortfolioComparisonResponse", description = "포트폴리오 비교 응답")
    public static class PortfolioComparisonResponse {
        @Schema(description = "포트폴리오 이름", example = "성장형")
        private String name;
        @Schema(description = "총 자산 금액(현재 평가금액)", example = "1250000")
        private BigDecimal totalAssetAmount;
        @Schema(description = "수익률(%)", example = "12.50")
        private BigDecimal returnRate;
        @Schema(description = "실현 수익(현재 구현: valuation.totalProfitLoss)", example = "140000")
        private BigDecimal realizedProfit;

        public static PortfolioComparisonResponse from(PortfolioGroup portfolioGroup) {
            PortfolioValuation valuation = portfolioGroup.getValuation();
            BigDecimal totalAssetAmount = BigDecimal.ZERO;
            BigDecimal returnRate = BigDecimal.ZERO;
            BigDecimal realizedProfit = BigDecimal.ZERO;

            if (valuation != null) {
                totalAssetAmount = valuation.getTotalCurrentValue() != null
                        ? valuation.getTotalCurrentValue()
                        : BigDecimal.ZERO;
                returnRate = valuation.getTotalReturnRate() != null
                        ? valuation.getTotalReturnRate()
                        : BigDecimal.ZERO;
                realizedProfit = valuation.getTotalProfitLoss() != null
                        ? valuation.getTotalProfitLoss()
                        : BigDecimal.ZERO;
            }

            return PortfolioComparisonResponse.builder()
                    .name(portfolioGroup.getName())
                    .totalAssetAmount(totalAssetAmount)
                    .returnRate(returnRate)
                    .realizedProfit(realizedProfit)
                    .build();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "AssetResponse", description = "자산 응답")
    public static class AssetResponse {
        @Schema(description = "자산 ID", example = "1")
        private Long id;
        @Schema(description = "자산 이름", example = "애플")
        private String name;
        @Schema(description = "보유 수량", example = "10")
        private BigDecimal amount;
        @Schema(description = "총 평가금액", example = "1800")
        private BigDecimal totalPrice;
        @Schema(description = "통화", example = "USD")
        private Currency currency;
        @Schema(description = "종목 ID", example = "101")
        private Long stockId;

        public static AssetResponse from(Asset asset) {
            return AssetResponse.builder()
                    .id(asset.getId())
                    .name(asset.getName())
                    .amount(asset.getAmount())
                    .totalPrice(asset.getTotalPrice().getAmount())
                    .currency(asset.getTotalPrice().getCurrency())
                    .stockId(asset.getStockId())
                    .build();
        }
    }
}
