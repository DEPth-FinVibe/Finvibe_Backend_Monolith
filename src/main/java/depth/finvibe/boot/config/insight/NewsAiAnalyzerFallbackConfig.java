package depth.finvibe.boot.config.insight;

import depth.finvibe.modules.news.application.port.out.NewsAiAnalyzer;
import depth.finvibe.modules.news.domain.EconomicSignal;
import depth.finvibe.modules.news.domain.NewsKeyword;
import depth.finvibe.common.insight.domain.CategoryInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NewsAiAnalyzerFallbackConfig {

    private static final Long DEFAULT_CATEGORY_ID = 4L;

    @Bean
    @ConditionalOnMissingBean(NewsAiAnalyzer.class)
    public NewsAiAnalyzer fallbackNewsAiAnalyzer() {
        return (content, categories) -> new NewsAiAnalyzer.AnalysisResult(
                "뉴스 분석에 실패하여 요약 정보를 제공할 수 없습니다.",
                EconomicSignal.NEUTRAL,
                NewsKeyword.ETF,
                resolveCategoryId(categories));
    }

    private Long resolveCategoryId(List<CategoryInfo> categories) {
        return categories.stream()
                .filter(category -> DEFAULT_CATEGORY_ID.equals(category.id()))
                .map(CategoryInfo::id)
                .findFirst()
                .orElse(categories.isEmpty() ? null : categories.get(0).id());
    }
}
