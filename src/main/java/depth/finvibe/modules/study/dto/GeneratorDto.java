package depth.finvibe.modules.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class GeneratorDto {
    @AllArgsConstructor(staticName = "of")
    @NoArgsConstructor
    @Data
    @Builder
    public static class LessonIndex {
        private String title;
        private String description;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class LessonIndexCreateRequest {
        private String courseTitle;
        private List<String> keywords;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class LessonContentCreateContext {
        private String courseTitle;
        private List<String> keywords;
        private String lessonTitle;
        private String lessonDescription;
    }
}
