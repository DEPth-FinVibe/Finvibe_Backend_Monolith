package depth.finvibe.modules.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;

public class PriceCandleDto {

    /**
     * 홈 목록의 미니 차트용 응답.
     * 종가 배열만 담아 종목당 캔들 전체를 내려보내지 않는다.
     */
    @Schema(name = "StockSparklineResponse", description = "종목 스파크라인 응답")
    public record SparklineResponse(
            @Schema(description = "종목 ID", example = "5280") Long stockId,
            @Schema(description = "오래된 순 종가 배열") List<BigDecimal> values
    ) {
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "PriceCandleResponse", description = "캔들 응답")
    public static class Response {
        @Schema(description = "시가", example = "70000")
        private BigDecimal open;
        @Schema(description = "종가", example = "70500")
        private BigDecimal close;
        @Schema(description = "고가", example = "71000")
        private BigDecimal high;
        @Schema(description = "저가", example = "69000")
        private BigDecimal low;
        @Schema(description = "거래량", example = "12000000")
        private BigDecimal volume;
        @Schema(description = "거래대금", example = "840000000000")
        private BigDecimal value;
        @Schema(description = "종목 ID", example = "1")
        private Long stockId;
        @Schema(description = "타임프레임", example = "DAY")
        private Timeframe timeframe;
        @Schema(description = "기준 시각", example = "2024-01-02T15:30:00")
        private LocalDateTime at;
        @Schema(description = "전일 대비 등락률", example = "0.5")
        private BigDecimal prevDayChangePct;

        public static Response from(PriceCandle priceCandle) {
            return Response.builder()
                    .open(priceCandle.getOpen())
                    .close(priceCandle.getClose())
                    .high(priceCandle.getHigh())
                    .low(priceCandle.getLow())
                    .volume(priceCandle.getVolume())
                    .value(priceCandle.getValue())
                    .stockId(priceCandle.getStockId())
                    .timeframe(priceCandle.getTimeframe())
                    .at(priceCandle.getAt())
                    .prevDayChangePct(priceCandle.getPrevDayChangePct())
                    .build();
        }
    }
}
