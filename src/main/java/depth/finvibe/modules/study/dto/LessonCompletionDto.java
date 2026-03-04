package depth.finvibe.modules.study.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

public class LessonCompletionDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "MonthlyLessonCompletionResponse", description = "월별 레슨 수료 이력 응답")
    public static class MonthlyLessonCompletionResponse {
        @Schema(description = "조회 월", example = "2026-02")
        private String month;
        @Schema(description = "수료 레슨 목록")
        private List<LessonCompletionItem> items;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "LessonCompletionItem", description = "수료 레슨 항목")
    public static class LessonCompletionItem {
        @Schema(description = "레슨 ID", example = "12")
        private Long lessonId;
        @Schema(description = "수료 일시", example = "2026-02-05T14:30:00")
        private LocalDateTime completedAt;
    }
}
