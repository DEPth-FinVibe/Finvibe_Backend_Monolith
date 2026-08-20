package depth.finvibe.modules.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.ClosingPrice;
import depth.finvibe.modules.market.domain.Stock;

public class ClosingPriceDto {

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @Schema(name = "ClosingPriceResponse", description = "종가 응답")
  public static class Response {

    @Schema(description = "종목 ID", example = "1")
    private Long stockId;
    @Schema(description = "종목명", example = "삼성전자")
    private String stockName;
    @Schema(description = "기준 시각", example = "2024-01-02T15:30:00")
    private LocalDateTime at;
    @Schema(description = "종가", example = "70000")
    private BigDecimal close;
    @Schema(description = "전일 대비 등락률", example = "0.5")
    private BigDecimal prevDayChangePct;
    @Schema(description = "거래량", example = "12000000")
    private BigDecimal volume;
    @Schema(description = "거래대금", example = "840000000000")
    private BigDecimal value;

    public static Response from(ClosingPrice closingPrice, Stock stock) {
      return Response.builder()
          .stockId(closingPrice.getStockId())
          .stockName(stock.getName())
          .at(closingPrice.getAt())
          .close(closingPrice.getClose())
          .prevDayChangePct(closingPrice.getPrevDayChangePct())
          .volume(closingPrice.getVolume())
          .value(closingPrice.getValue())
          .build();
    }
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @Schema(name = "ClosingPriceBatchResponse", description = "종가 일괄 조회 응답")
  public static class BatchResponse {

    @Schema(description = "확보한 종가 목록")
    private List<Response> items;
    @Schema(description = "종가를 확보하지 못한 종목 ID")
    private List<Long> missingStockIds;
    @Schema(description = "일부 종목의 종가가 누락되었는지 여부", example = "false")
    private boolean partial;
    @Schema(description = "종가 기준 거래일", example = "2026-08-21")
    private LocalDate tradingDate;
    @Schema(description = "응답 생성 기준 시각(Asia/Seoul)", example = "2026-08-21T16:00:00")
    private LocalDateTime asOf;

    public static BatchResponse of(
        List<Response> items,
        List<Long> missingStockIds,
        LocalDate tradingDate,
        LocalDateTime asOf
    ) {
      return BatchResponse.builder()
          .items(items)
          .missingStockIds(missingStockIds)
          .partial(!missingStockIds.isEmpty())
          .tradingDate(tradingDate)
          .asOf(asOf)
          .build();
    }
  }
}
