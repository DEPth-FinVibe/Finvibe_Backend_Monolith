package depth.finvibe.modules.gamification.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import depth.finvibe.modules.gamification.domain.enums.Badge;

public class BadgeDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(description = "배지 정보")
    public static class BadgeInfo {
        @Schema(description = "배지 코드")
        private Badge badge;
        @Schema(description = "배지 표시 이름")
        private String displayName;
        @Schema(description = "획득 일시")
        private LocalDateTime acquiredAt;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(description = "배지 통계 정보")
    public static class BadgeStatistics {
        @Schema(description = "배지 코드")
        private Badge badge;
        @Schema(description = "배지 표시 이름")
        private String displayName;
    }
}
