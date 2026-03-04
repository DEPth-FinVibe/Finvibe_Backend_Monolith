package depth.finvibe.modules.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import depth.finvibe.modules.market.domain.Category;

public class CategoryInternalDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long categoryId;
        private String categoryName;

        public static Response of(Category category) {
            return Response.builder()
                    .categoryId(category.getId())
                    .categoryName(category.getName())
                    .build();
        }
    }
}
