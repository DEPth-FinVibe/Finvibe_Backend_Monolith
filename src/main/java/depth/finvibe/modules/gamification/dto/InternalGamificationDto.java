package depth.finvibe.modules.gamification.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

import io.swagger.v3.oas.annotations.media.Schema;

import depth.finvibe.modules.gamification.domain.BadgeOwnership;
import depth.finvibe.modules.gamification.domain.enums.Badge;

public class InternalGamificationDto {

    @Getter
    @Builder
    @Schema(description = "유저 게이미피케이션 요약 정보")
    public static class UserSummaryResponse {
        @Schema(description = "사용자 UUID")
        private UUID userId;
        @Schema(description = "보유 뱃지 목록")
        private List<OwnedBadge> badges;
        @Schema(description = "주간 XP 랭킹 순위")
        private Integer ranking;
        @Schema(description = "누적 보유 XP")
        private Long totalXp;
        @Schema(description = "현재 수익률")
        private Double currentReturnRate;
    }

    @Getter
    @Builder
    @Schema(description = "보유 뱃지")
    public static class OwnedBadge {
        @Schema(description = "뱃지 코드")
        private Badge badge;
        @Schema(description = "뱃지 표시 이름")
        private String displayName;
        @Schema(description = "획득 일시")
        private LocalDateTime acquiredAt;

        public static OwnedBadge from(BadgeOwnership badgeOwnership) {
            return OwnedBadge.builder()
                    .badge(badgeOwnership.getBadge())
                    .displayName(badgeOwnership.getBadge().getDisplayName())
                    .acquiredAt(badgeOwnership.getCreatedAt())
                    .build();
        }
    }
}
