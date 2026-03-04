package depth.finvibe.modules.trade.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.trade.domain.Trade;
import depth.finvibe.modules.trade.domain.enums.TradeType;
import depth.finvibe.modules.trade.domain.enums.TransactionType;

public class TradeDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TransactionRequest", description = "거래 생성 요청")
    public static class TransactionRequest {
        @Schema(description = "종목 ID", example = "10")
        private Long stockId;
        @Schema(description = "주문 수량", example = "5")
        private Double amount;
        @Schema(description = "주문 가격", example = "60000")
        private Long price;
        @Schema(description = "포트폴리오 ID", example = "1")
        private Long portfolioId;
        @Schema(description = "거래 유형", example = "NORMAL")
        private TradeOrderType tradeType;
        @Schema(description = "매수/매도 구분", example = "BUY")
        private TransactionType transactionType;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TradeResponse", description = "거래 응답")
    public static class TradeResponse {
        @Schema(description = "거래 ID", example = "123")
        private Long tradeId;
        @Schema(description = "종목 ID", example = "10")
        private Long stockId;
        @Schema(description = "주문 수량", example = "5")
        private Double amount;
        @Schema(description = "주문 가격", example = "60000")
        private Long price;
        @Schema(description = "포트폴리오 ID", example = "1")
        private Long portfolioId;
        @Schema(description = "사용자 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID userId;
        @Schema(description = "거래 유형", example = "NORMAL")
        private TradeType tradeType;
        @Schema(description = "매수/매도 구분", example = "BUY")
        private TransactionType transactionType;

        public static TradeResponse from(Trade trade) {
            return TradeResponse.builder()
                    .tradeId(trade.getId())
                    .stockId(trade.getStockId())
                    .amount(trade.getAmount())
                    .price(trade.getPrice())
                    .portfolioId(trade.getPortfolioId())
                    .userId(trade.getUserId())
                    .tradeType(trade.getTradeType())
                    .transactionType(trade.getTransactionType())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TradeHistoryResponse", description = "거래 기록 응답")
    public static class TradeHistoryResponse {
        @Schema(description = "거래 ID", example = "123")
        private Long tradeId;
        @Schema(description = "종목 ID", example = "10")
        private Long stockId;
        @Schema(description = "종목명", example = "삼성전자")
        private String stockName;
        @Schema(description = "주문 수량", example = "5")
        private Double amount;
        @Schema(description = "주문 가격", example = "60000")
        private Long price;
        @Schema(description = "포트폴리오 ID", example = "1")
        private Long portfolioId;
        @Schema(description = "매수/매도 구분", example = "BUY")
        private TransactionType transactionType;
        @Schema(description = "거래 유형", example = "NORMAL")
        private TradeType tradeType;
        @Schema(description = "거래 생성일시")
        private LocalDateTime createdAt;

        public static TradeHistoryResponse from(Trade trade) {
            return TradeHistoryResponse.builder()
                    .tradeId(trade.getId())
                    .stockId(trade.getStockId())
                    .stockName(trade.getStockName())
                    .amount(trade.getAmount())
                    .price(trade.getPrice())
                    .portfolioId(trade.getPortfolioId())
                    .transactionType(trade.getTransactionType())
                    .tradeType(trade.getTradeType())
                    .createdAt(trade.getCreatedAt())
                    .build();
        }
    }
}
