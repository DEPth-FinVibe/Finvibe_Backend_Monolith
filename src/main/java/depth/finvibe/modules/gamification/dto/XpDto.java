package depth.finvibe.modules.gamification.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import depth.finvibe.modules.gamification.domain.UserXp;

public class XpDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "관리자 XP 지급 요청")
    public static class GrantXpRequest {
        @Schema(description = "지급할 XP", example = "100")
        private Long value;
        @Schema(description = "지급 사유", example = "이벤트 수동 보상")
        private String reason;
    }

    @Getter
    @Builder
    @Schema(description = "사용자 경험치 정보")
    public static class Response {
        @Schema(description = "사용자 UUID")
        private UUID userId;
        @Schema(description = "닉네임")
        private String nickname;
        @Schema(description = "누적 경험치")
        private Long totalXp;
        @Schema(description = "현재 레벨")
        private Integer level;

        public static Response from(UserXp userXp) {
            return Response.builder()
                    .userId(userXp.getUserId())
                    .nickname(userXp.getNickname())
                    .totalXp(userXp.getTotalXp())
                    .level(userXp.getLevel())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "스쿼드 경험치 랭킹")
    public static class SquadRankingResponse {
        @Schema(description = "스쿼드 식별자")
        private Long squadId;
        @Schema(description = "스쿼드 이름")
        private String squadName;
        @Schema(description = "현재 순위")
        private Integer currentRanking;
        @Schema(description = "누적 경험치")
        private Long totalXp;
        @Schema(description = "주간 경험치")
        private Long weeklyXp;
        @Schema(description = "주간 경험치 증감률")
        private Double weeklyXpChangeRate;
        @Schema(description = "순위 변동")
        private Integer rankingChange; // +2, -1, 0 등
    }

    @Getter
    @Builder
    @Schema(description = "스쿼드 기여도 랭킹")
    public static class ContributionRankingResponse {
        @Schema(description = "닉네임")
        private String nickname;
        @Schema(description = "순위")
        private Integer ranking;
        @Schema(description = "주간 기여 경험치")
        private Long weeklyContributionXp;
    }

    @Getter
    @Builder
    @Schema(description = "전체 사용자 XP 랭킹")
    public static class UserRankingResponse {
        @Schema(description = "사용자 UUID")
        private UUID userId;
        @Schema(description = "닉네임")
        private String nickname;
        @Schema(description = "순위")
        private Integer ranking;
        @Schema(description = "현재 누적 XP")
        private Long currentXp;
        @Schema(description = "현재 기간 XP")
        private Long periodXp;
        @Schema(description = "이전 기간 XP")
        private Long previousPeriodXp;
        @Schema(description = "이전 기간 대비 상승률(%)")
        private Double growthRate;
    }
}
