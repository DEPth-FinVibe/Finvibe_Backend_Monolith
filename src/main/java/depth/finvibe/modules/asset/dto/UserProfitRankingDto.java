package depth.finvibe.modules.asset.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

public class UserProfitRankingDto {

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  @Builder
  @Schema(name = "UserProfitRankingItem", description = "사용자 수익률 랭킹 항목")
  public static class RankingItem {
    @Schema(description = "순위", example = "1")
    private Integer rank;
    @Schema(description = "사용자 ID")
    private UUID userId;
    @Schema(description = "사용자 닉네임", example = "투자고수")
    private String nickname;
    @Schema(description = "수익률", example = "12.34")
    private BigDecimal returnRate;
    @Schema(description = "수익금", example = "150000")
    private BigDecimal profitLoss;

    public static RankingItem from(UserProfitRanking ranking) {
      return RankingItem.builder()
        .rank(ranking.getRank())
        .userId(ranking.getUserId())
        .nickname(ranking.getUserNickname())
        .returnRate(ranking.getTotalReturnRate())
        .profitLoss(ranking.getTotalProfitLoss())
        .build();
    }
  }

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  @Builder
  @Schema(name = "UserProfitRankingPageResponse", description = "사용자 수익률 랭킹 응답")
  public static class RankingPageResponse {
    @Schema(description = "랭킹 타입", example = "WEEKLY")
    private UserProfitRankType rankType;
    @Schema(description = "페이지 번호", example = "0")
    private int page;
    @Schema(description = "페이지 크기", example = "50")
    private int size;
    @Schema(description = "전체 요소 수", example = "1234")
    private long totalElements;
    @Schema(description = "전체 페이지 수", example = "25")
    private int totalPages;
    @Schema(description = "랭킹 목록")
    private List<RankingItem> items;
  }
}
