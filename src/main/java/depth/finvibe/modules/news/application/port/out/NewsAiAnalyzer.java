package depth.finvibe.modules.news.application.port.out;

import depth.finvibe.modules.news.domain.EconomicSignal;
import depth.finvibe.modules.news.domain.NewsKeyword;
import depth.finvibe.common.insight.domain.CategoryInfo;

import java.util.List;

public interface NewsAiAnalyzer {
    AnalysisResult analyze(String content, List<CategoryInfo> categories);

    record AnalysisResult(String summary, EconomicSignal signal, NewsKeyword keyword, Long categoryId) {
    }
}
