package depth.finvibe.modules.asset.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TopHoldingStockDto {

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  @Builder
  @Schema(name = "TopHoldingStockResponse", description = "보유 수량 TOP 종목 응답")
  public static class TopHoldingStockResponse {
    @Schema(description = "종목 ID", example = "101")
    private Long stockId;
    @Schema(description = "종목명", example = "애플")
    private String name;
    @Schema(description = "총 보유 수량", example = "12.5")
    private BigDecimal totalAmount;
  }

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  @Builder
  @Schema(name = "TopHoldingStockListResponse", description = "보유 수량 TOP 종목 응답")
  public static class TopHoldingStockListResponse {
    @Schema(description = "전체 요소 수", example = "50")
    private int totalElements;
    @Schema(description = "보유 종목 목록")
    private List<TopHoldingStockResponse> items;
  }
}
