package depth.finvibe.modules.news.application;

import depth.finvibe.modules.news.application.port.in.NewsCommandUseCase;
import depth.finvibe.modules.news.application.port.out.NewsAiAnalyzer;
import depth.finvibe.modules.news.application.port.out.NewsDiscussionPort;
import depth.finvibe.modules.news.application.port.out.NewsCrawler;
import depth.finvibe.modules.news.application.port.out.NewsLikeRepository;
import depth.finvibe.modules.news.application.port.out.NewsRepository;
import depth.finvibe.modules.news.application.port.out.CategoryCatalogPort;
import depth.finvibe.modules.news.domain.News;
import depth.finvibe.modules.news.domain.NewsLike;
import depth.finvibe.modules.news.domain.error.NewsErrorCode;
import depth.finvibe.common.insight.application.port.out.UserMetricEventPort;
import depth.finvibe.common.insight.domain.CategoryInfo;
import depth.finvibe.common.insight.dto.MetricEventType;
import depth.finvibe.common.insight.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NewsCommandService implements NewsCommandUseCase {

    private static final Long DEFAULT_CATEGORY_ID = 4L;
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_PROVIDER = "NAVER";

    private final NewsRepository newsRepository;
    private final NewsLikeRepository newsLikeRepository;
    private final NewsCrawler newsCrawler;
    private final NewsAiAnalyzer newsAiAnalyzer;
    private final NewsDiscussionPort newsDiscussionPort;
    private final CategoryCatalogPort categoryCatalogPort;
    private final UserMetricEventPort userMetricEventPort;

    @Override
    public void syncLatestNews() {
        List<NewsCrawler.RawNewsData> rawDataList = newsCrawler.fetchLatestRawNews();
        List<CategoryInfo> categories = categoryCatalogPort.getAll();
        if (categories.isEmpty()) {
            throw new IllegalStateException("No categories available from market catalog");
        }
        CategoryInfo defaultCategory = resolveDefaultCategory(categories);

        for (NewsCrawler.RawNewsData rawData : rawDataList) {
            if (newsRepository.existsByTitle(rawData.title())) {
                continue;
            }

            String contentText = resolveAnalysisText(rawData);
            String analysisInput = "제목: " + rawData.title() + "\n요약: " + contentText;
            NewsAiAnalyzer.AnalysisResult analysis = newsAiAnalyzer.analyze(analysisInput, categories);
            CategoryInfo category = resolveCategory(analysis.categoryId(), categories, defaultCategory);

            LocalDateTime publishedAt = rawData.publishedAt() != null
                    ? rawData.publishedAt()
                    : LocalDateTime.now(KST_ZONE);
            String provider = (rawData.provider() == null || rawData.provider().isBlank())
                    ? DEFAULT_PROVIDER
                    : rawData.provider();

            News news = News.create(
                    rawData.title(),
                    rawData.contentHtml(),
                    contentText,
                    analysis.summary(),
                    analysis.signal(),
                    analysis.keyword(),
                    category.id(),
                    category.name(),
                    publishedAt,
                    provider);

            newsRepository.save(news);
        }
    }

    @Override
    public void syncAllDiscussionCounts() {
        List<News> allNews = newsRepository.findAll();
        List<Long> newsIds = allNews.stream().map(News::getId).toList();

        // 벌크로 토론 수 조회 (네트워크 호출 최소화)
        java.util.Map<Long, Long> countsMap = newsDiscussionPort.getDiscussionCounts(newsIds);

        for (News news : allNews) {
            long currentCount = countsMap.getOrDefault(news.getId(), 0L);

            if (news.getDiscussionCount() != currentCount) {
                news.syncDiscussionCount(currentCount);
                newsRepository.save(news);
            }
        }
    }

    @Override
    public void toggleNewsLike(Long newsId, UUID userId) {
        newsLikeRepository.findByNewsIdAndUserId(newsId, userId)
                .ifPresentOrElse(existingLike -> {
                            newsLikeRepository.delete(existingLike);
                            userMetricEventPort.publish(
                                    userId.toString(),
                                    MetricEventType.NEWS_LIKE_COUNT,
                                    -1.0,
                                    Instant.now());
                        },
                        () -> {
                            News news = newsRepository.findById(newsId)
                                    .orElseThrow(() -> new DomainException(NewsErrorCode.NEWS_NOT_FOUND));
                            newsLikeRepository.save(NewsLike.create(news, userId));
                            userMetricEventPort.publish(
                                    userId.toString(),
                                    MetricEventType.NEWS_LIKE_COUNT,
                                    1.0,
                                    Instant.now());
                        });
    }

    private CategoryInfo resolveDefaultCategory(List<CategoryInfo> categories) {
        return categories.stream()
                .filter(category -> DEFAULT_CATEGORY_ID.equals(category.id()))
                .findFirst()
                .orElse(categories.get(0));
    }

    private CategoryInfo resolveCategory(Long categoryId, List<CategoryInfo> categories, CategoryInfo fallback) {
        if (categoryId == null) {
            return fallback;
        }
        return categories.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst()
                .orElse(fallback);
    }

    private String resolveAnalysisText(NewsCrawler.RawNewsData rawData) {
        if (rawData.contentText() != null && !rawData.contentText().isBlank()) {
            return rawData.contentText();
        }
        if (rawData.contentHtml() != null && !rawData.contentHtml().isBlank()) {
            return Jsoup.parse(rawData.contentHtml()).text();
        }
        return "";
    }
}
