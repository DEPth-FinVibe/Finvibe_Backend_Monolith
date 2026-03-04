package depth.finvibe.modules.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.enums.MarketStatus;

public class MarketStatusDto {

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  @Schema(name = "MarketStatusResponse", description = "장 상태 응답")
  public static class Response {

    @Schema(description = "장 상태", example = "OPEN")
    private MarketStatus status;

    public static Response from(MarketStatus status) {
      return Response.builder()
          .status(status)
          .build();
    }
  }
}
