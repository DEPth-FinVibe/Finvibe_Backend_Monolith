package depth.finvibe.modules.market.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.domain.enums.MarketStatusReason;

public class MarketStatusDto {

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @Schema(name = "MarketStatusResponse", description = "장 상태 응답")
  public static class Response {

    @Schema(description = "장 상태", example = "OPEN")
    private MarketStatus status;
    @Schema(description = "장 상태 판정 사유", example = "TRADING_HOURS")
    private MarketStatusReason reason;
    @Schema(description = "최근 거래 기준일", example = "2026-08-21")
    private LocalDate tradingDate;
    @Schema(description = "판정 기준 시각(Asia/Seoul)", example = "2026-08-21T10:30:00")
    private LocalDateTime asOf;

    public static Response from(MarketStatus status) {
      return Response.builder()
          .status(status)
          .build();
    }

    public static Response of(
        MarketStatus status,
        MarketStatusReason reason,
        LocalDate tradingDate,
        LocalDateTime asOf
    ) {
      return Response.builder()
          .status(status)
          .reason(reason)
          .tradingDate(tradingDate)
          .asOf(asOf)
          .build();
    }
  }
}
