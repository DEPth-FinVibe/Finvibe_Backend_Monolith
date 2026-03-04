package depth.finvibe.modules.gamification.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import depth.finvibe.modules.gamification.domain.PersonalChallenge;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;

public class ChallengeDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(description = "개인 챌린지 생성 결과")
    public static class ChallengeGenerationResponse {
        @Schema(description = "챌린지 제목")
        private String title;

        @Schema(description = "챌린지 설명")
        private String description;

        @Schema(description = "측정 지표 타입")
        private UserMetricType metricType;

        @Schema(description = "목표 값")
        private Double targetValue;

        @Schema(description = "보상 경험치")
        private Long rewardXp;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(description = "개인 챌린지 응답")
    public static class ChallengeResponse {
        @Schema(description = "챌린지 식별자")
        private Long id;
        @Schema(description = "챌린지 제목")
        private String title;
        @Schema(description = "챌린지 설명")
        private String description;
        @Schema(description = "측정 지표 타입")
        private UserMetricType metricType;
        @Schema(description = "목표 값")
        private Double targetValue;
        @Schema(description = "현재 값")
        private Double currentValue;
        @Schema(description = "진행률(%)")
        private Double progressPercentage;
        @Schema(description = "보상 경험치")
        private Long rewardXp;
        @Schema(description = "시작일")
        private LocalDate startDate;
        @Schema(description = "종료일")
        private LocalDate endDate;
        @Schema(description = "달성 여부")
        private boolean isAchieved;

        public static ChallengeResponse from(PersonalChallenge challenge, Double currentValue) {
            Double targetValue = challenge.getCondition().getTargetValue();
            return ChallengeResponse.builder()
                    .id(challenge.getId())
                    .title(challenge.getTitle())
                    .description(challenge.getDescription())
                    .metricType(challenge.getCondition().getMetricType())
                    .targetValue(targetValue)
                    .currentValue(currentValue)
                    .progressPercentage(Math.min(100.0, (currentValue / targetValue) * 100.0))
                    .rewardXp(challenge.getReward().getRewardXp())
                    .startDate(challenge.getPeriod().getStartDate())
                    .endDate(challenge.getPeriod().getEndDate())
                    .isAchieved(currentValue >= targetValue)
                    .build();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(description = "챌린지 완료 내역 응답")
    public static class ChallengeHistoryResponse {
        @Schema(description = "챌린지 식별자")
        private Long challengeId;
        @Schema(description = "챌린지 제목")
        private String title;
        @Schema(description = "챌린지 설명")
        private String description;
        @Schema(description = "측정 지표 타입")
        private UserMetricType metricType;
        @Schema(description = "목표 값")
        private Double targetValue;
        @Schema(description = "보상 경험치")
        private Long rewardXp;
        @Schema(description = "챌린지 시작일")
        private LocalDate startDate;
        @Schema(description = "챌린지 종료일")
        private LocalDate endDate;
        @Schema(description = "완료 시점")
        private LocalDate completedAt;

        public static ChallengeHistoryResponse from(PersonalChallenge challenge, LocalDate completedAt) {
            return ChallengeHistoryResponse.builder()
                    .challengeId(challenge.getId())
                    .title(challenge.getTitle())
                    .description(challenge.getDescription())
                    .metricType(challenge.getCondition().getMetricType())
                    .targetValue(challenge.getCondition().getTargetValue())
                    .rewardXp(challenge.getReward().getRewardXp())
                    .startDate(challenge.getPeriod().getStartDate())
                    .endDate(challenge.getPeriod().getEndDate())
                    .completedAt(completedAt)
                    .build();
        }
    }
}
