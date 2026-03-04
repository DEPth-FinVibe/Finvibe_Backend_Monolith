package depth.finvibe.modules.study.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.study.domain.StudyMetric;

public class StudyMetricDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "MyStudyMetricResponse", description = "내 학습 지표 응답")
    public static class MyMetricResponse {
        @Schema(description = "누적 획득 XP", example = "120")
        private Long xpEarned;

        @Schema(description = "누적 학습 시간(분)", example = "90")
        private Long timeSpentMinutes;

        @Schema(description = "마지막 1분 핑 시각(UTC)", example = "2026-02-09T09:10:11Z")
        private Instant lastPingAt;

        public static MyMetricResponse from(StudyMetric studyMetric) {
            return MyMetricResponse.builder()
                    .xpEarned(studyMetric.getXpEarned() == null ? 0L : studyMetric.getXpEarned())
                    .timeSpentMinutes(studyMetric.getTimeSpentMinutes() == null ? 0L : studyMetric.getTimeSpentMinutes())
                    .lastPingAt(studyMetric.getLastPingAt())
                    .build();
        }

        public static MyMetricResponse empty() {
            return MyMetricResponse.builder()
                    .xpEarned(0L)
                    .timeSpentMinutes(0L)
                    .lastPingAt(null)
                    .build();
        }
    }
}
