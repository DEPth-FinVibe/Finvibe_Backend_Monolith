package depth.finvibe.modules.study.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.study.domain.Course;
import depth.finvibe.modules.study.domain.CourseDifficulty;

public class CourseDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "CourseCreateRequest", description = "코스 생성 요청")
    public static class CreateRequest {
        @Schema(description = "코스 제목", example = "ETF 기초 입문")
        private String title;
        @Schema(description = "학습 키워드 목록", example = "[\"ETF\", \"분산 투자\"]")
        private List<String> keywords;
        @Schema(description = "난이도", example = "BEGINNER")
        private CourseDifficulty difficulty;
    }

    @AllArgsConstructor(staticName = "of")
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "CourseContentPreviewResponse", description = "코스 소개 미리보기 응답")
    public static class ContentPreviewResponse {
        @Schema(description = "생성된 코스 소개", example = "이 코스는 ETF의 기본 구조와 투자 원칙을 학습합니다. (마크다운 형식)")
        private String content;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    @Schema(name = "MyCourseResponse", description = "내 코스 상세 응답")
    public static class MyCourseResponse {
        @Schema(description = "코스 ID", example = "1")
        private Long id;
        @Schema(description = "코스 제목", example = "ETF 기초 입문")
        private String title;
        @Schema(description = "코스 설명", example = "ETF 투자 기초를 단계별로 학습합니다.")
        private String description;
        @Schema(description = "난이도", example = "BEGINNER")
        private CourseDifficulty difficulty;
        @Schema(description = "총 레슨 수", example = "5")
        private Integer totalLessonCount;
        @Schema(description = "레슨 요약 목록")
        private List<LessonDto.LessonSummary> lessons;

        public static MyCourseResponse from(Course course, List<LessonDto.LessonSummary> lessons) {
            return MyCourseResponse.builder()
                    .id(course.getId())
                    .title(course.getTitle())
                    .description(course.getDescription())
                    .difficulty(course.getDifficulty())
                    .totalLessonCount(course.getTotalLessonCount())
                    .lessons(lessons)
                    .build();
        }
    }
}
