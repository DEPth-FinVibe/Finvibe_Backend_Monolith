package depth.finvibe.modules.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.enums.Timeframe;

public class PriceCandleDto {

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
