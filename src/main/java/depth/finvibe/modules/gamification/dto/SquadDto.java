package depth.finvibe.modules.gamification.dto;

import lombok.Builder;
import lombok.Getter;

import io.swagger.v3.oas.annotations.media.Schema;

public class SquadDto {

    @Getter
    @Builder
    @Schema(description = "스쿼드 정보")
    public static class Response {
        @Schema(description = "스쿼드 식별자")
        private Long id;
        @Schema(description = "스쿼드 이름")
        private String name;
        @Schema(description = "스쿼드 지역")
        private String region;
    }
}
