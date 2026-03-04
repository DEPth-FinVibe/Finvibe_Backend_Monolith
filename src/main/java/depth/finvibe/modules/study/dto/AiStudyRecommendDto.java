package depth.finvibe.modules.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AiStudyRecommendDto {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class GetTodayAiStudyRecommendResponse {
        private String content;
    }
}
