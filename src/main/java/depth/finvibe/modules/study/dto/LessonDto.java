package depth.finvibe.modules.study.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.study.domain.Lesson;

public class LessonDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "LessonSummary", description = "레슨 요약")
    public static class LessonSummary {
        @Schema(description = "레슨 ID", example = "10")
        private Long id;
        @Schema(description = "레슨 제목", example = "ETF란 무엇인가")
        private String title;
        @Schema(description = "레슨 설명", example = "ETF의 정의와 기본 구조를 학습합니다.")
        private String description;
        @Schema(description = "완료 여부", example = "false")
        private boolean completed;

        public static LessonSummary from(Lesson lesson, boolean completed) {
            return LessonSummary.builder()
                    .id(lesson.getId())
                    .title(lesson.getTitle())
                    .description(lesson.getDescription())
                    .completed(completed)
                    .build();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "LessonDetailResponse", description = "레슨 상세 응답")
    public static class LessonDetailResponse {
        @Schema(description = "레슨 ID", example = "10")
        private Long id;
        @Schema(description = "레슨 제목", example = "ETF란 무엇인가")
        private String title;
        @Schema(description = "레슨 설명", example = "ETF의 정의와 기본 구조를 학습합니다.")
        private String description;
        @Schema(description = "레슨 본문", example = "ETF는 특정 지수를 추종하는 펀드입니다. (마크다운 형식)")
        private String content;
        @Schema(description = "완료 여부", example = "true")
        private boolean completed;

        public static LessonDetailResponse from(Lesson lesson, String content, boolean completed) {
            return LessonDetailResponse.builder()
                    .id(lesson.getId())
                    .title(lesson.getTitle())
                    .description(lesson.getDescription())
                    .content(content)
                    .completed(completed)
                    .build();
        }
    }
}
