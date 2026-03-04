package depth.finvibe.modules.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.RankType;

public class StockDto {
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "StockResponse", description = "종목 응답")
    public static class Response {
        @Schema(description = "종목 ID", example = "1")
        private Long stockId;
        @Schema(description = "종목 코드", example = "005930")
        private String symbol;
        @Schema(description = "종목명", example = "삼성전자")
        private String name;
        @Schema(description = "카테고리 ID", example = "10")
        private Long categoryId;

        public static Response from(Stock stock) {
            return Response.builder()
                    .stockId(stock.getId())
                    .name(stock.getName())
                    .symbol(stock.getSymbol())
                    .categoryId(stock.getCategoryId())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TopStockResponse", description = "TOP 종목 응답")
    public static class TopStockResponse {
        @Schema(description = "종목 코드", example = "005930")
        private String symbol;

        public static TopStockResponse from(Stock stock){
            return TopStockResponse.builder()
                    .symbol(stock.getSymbol())
                    .build();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "RealMarketStockResponse", description = "실시간 종목 응답")
    public static class RealMarketStockResponse {
        @Schema(description = "종목 코드", example = "005930")
        private String symbol;
        @Schema(description = "종목명", example = "삼성전자")
        private String name;
        @Schema(description = "표준산업분류 중분류 코드", example = "10")
        private String typeCode; // 표준산업분류코드 (중분류)
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "RankingResponse", description = "종목 순위 응답")
    public static class RankingResponse {
        @Schema(description = "종목 코드", example = "005930")
        private String symbol;
        @Schema(description = "순위 타입", example = "VOLUME")
        private RankType rankType;
        @Schema(description = "순위", example = "1")
        private Integer rank;
    }
}
