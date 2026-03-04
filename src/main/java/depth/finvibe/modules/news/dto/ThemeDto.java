package depth.finvibe.modules.news.dto;

import depth.finvibe.modules.news.domain.News;
import depth.finvibe.modules.news.domain.ThemeDaily;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ThemeDto {

    @Getter
    public static class SummaryResponse {
        private final Long categoryId;
        private final String categoryName;
        private final java.math.BigDecimal averageChangePct;

        public SummaryResponse(ThemeDaily themeDaily, java.math.BigDecimal averageChangePct) {
            this.categoryId = themeDaily.getCategoryId();
            this.categoryName = themeDaily.getCategoryName();
            this.averageChangePct = averageChangePct;
        }
    }

    @Getter
    public static class DetailResponse {
        private final Long categoryId;
        private final String categoryName;
        private final String analysis;
        private final List<NewsSummary> news;

        public DetailResponse(ThemeDaily themeDaily, List<NewsSummary> news) {
            this.categoryId = themeDaily.getCategoryId();
            this.categoryName = themeDaily.getCategoryName();
            this.analysis = themeDaily.getAnalysis() == null ? "" : themeDaily.getAnalysis();
            this.news = news;
        }
    }

    @Getter
    public static class NewsSummary {
        private final String title;
        private final LocalDateTime publishedAt;
        private final String provider;

        public NewsSummary(News news) {
            this.title = news.getTitle();
            this.publishedAt = news.getPublishedAt();
            this.provider = news.getProvider();
        }
    }
}
