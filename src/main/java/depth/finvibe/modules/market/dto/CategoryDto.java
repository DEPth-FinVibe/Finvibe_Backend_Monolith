package depth.finvibe.modules.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.BatchUpdatePrice;
import depth.finvibe.modules.market.domain.Category;
import depth.finvibe.modules.market.domain.Stock;

public class CategoryDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CategoryResponse", description = "카테고리 응답")
    public static class Response {
        @Schema(description = "카테고리 ID", example = "10")
        private Long categoryId;
        @Schema(description = "카테고리 이름", example = "반도체")
        private String categoryName;
        @Schema(description = "종목 수", example = "45")
        private Integer stockCount;

        public static Response of(Category category, int stockCount) {
            return Response.builder()
                    .categoryId(category.getId())
                    .categoryName(category.getName())
                    .stockCount(stockCount)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CategoryChangeRateResponse", description = "카테고리 등락률 응답")
    public static class ChangeRateResponse {
        @Schema(description = "카테고리 ID", example = "10")
        private Long categoryId;
        @Schema(description = "카테고리 이름", example = "반도체")
        private String categoryName;
        @Schema(description = "평균 등락률 (%)", example = "1.23")
        private BigDecimal averageChangePct;
        @Schema(description = "총 종목 수", example = "45")
        private Integer stockCount;
        @Schema(description = "상승 종목 수", example = "30")
        private Integer positiveCount;
        @Schema(description = "하락 종목 수", example = "15")
        private Integer negativeCount;
        @Schema(description = "데이터 업데이트 시각", example = "2024-02-04T15:30:00")
        private LocalDateTime updatedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CategoryStockValueResponse", description = "카테고리 종목 거래대금 응답")
    public static class StockValueResponse {
        @Schema(description = "종목 ID", example = "1")
        private Long stockId;
        @Schema(description = "종목 코드", example = "005930")
        private String symbol;
        @Schema(description = "종목명", example = "삼성전자")
        private String name;
        @Schema(description = "현재가", example = "71000")
        private BigDecimal currentPrice;
        @Schema(description = "전일 대비 등락률", example = "1.5")
        private BigDecimal prevDayChangePct;
        @Schema(description = "거래량", example = "12000000")
        private BigDecimal volume;
        @Schema(description = "거래대금", example = "852000000000")
        private BigDecimal value;
        @Schema(description = "거래대금 순위", example = "1")
        private Integer rank;

        public static StockValueResponse of(Stock stock, BatchUpdatePrice batchUpdatePrice, int rank) {
            return StockValueResponse.builder()
                    .stockId(stock.getId())
                    .symbol(stock.getSymbol())
                    .name(stock.getName())
                    .currentPrice(batchUpdatePrice.getPrice())
                    .prevDayChangePct(batchUpdatePrice.getPrevDayChangePct())
                    .volume(batchUpdatePrice.getVolume())
                    .value(batchUpdatePrice.getValue())
                    .rank(rank)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CategoryStockListResponse", description = "카테고리 종목 목록 응답")
    public static class StockListResponse {
        @Schema(description = "카테고리 ID", example = "10")
        private Long categoryId;
        @Schema(description = "카테고리 이름", example = "반도체")
        private String categoryName;
        @Schema(description = "종목 목록")
        private List<StockValueResponse> stocks;
    }
}
