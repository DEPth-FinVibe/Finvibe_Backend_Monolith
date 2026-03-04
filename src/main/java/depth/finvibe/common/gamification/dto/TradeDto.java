package depth.finvibe.common.gamification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class TradeDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TradeHistoryResponse", description = "거래 기록 응답")
    public static class TradeHistoryResponse {
        private Long tradeId;
        private Long stockId;
        private String stockName;
        private Double amount;
        private Long price;
        private Long portfolioId;
        private TransactionType transactionType;
        private TradeType tradeType;
        private LocalDateTime createdAt;
    }

    public enum TradeType {
        NORMAL, // 일반
        RESERVED, // 예약
        CANCELLED // 취소
    }

    public enum TransactionType {
        BUY, // 매수
        SELL // 매도
    }

}
