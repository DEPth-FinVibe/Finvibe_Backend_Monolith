package depth.finvibe.modules.news.application;

import depth.finvibe.modules.news.application.port.in.NewsQueryUseCase;
import depth.finvibe.modules.news.application.port.out.NewsDiscussionPort;
import depth.finvibe.modules.news.application.port.out.NewsLikeRepository;
import depth.finvibe.modules.news.application.port.out.NewsRepository;
import depth.finvibe.modules.news.domain.News;
import depth.finvibe.modules.news.domain.NewsKeyword;
import depth.finvibe.modules.news.domain.error.NewsErrorCode;
import depth.finvibe.modules.news.dto.NewsDto;
import depth.finvibe.modules.news.dto.NewsSortType;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsQueryService implements NewsQueryUseCase {

    private final NewsRepository newsRepository;
    private final NewsLikeRepository newsLikeRepository;
    private final NewsDiscussionPort newsDiscussionPort;
    private static final int DAILY_KEYWORD_LIMIT = 5;
    private static final int RECENT_NEWS_WINDOW_SIZE = 30;
    private static final List<NewsKeyword> KEYWORD_FALLBACK_ORDER = List.of(
            NewsKeyword.AI,
            NewsKeyword.ETF,
            NewsKeyword.SEMICONDUCTOR,
            NewsKeyword.INFLATION,
            NewsKeyword.RATE_CUT);

    @Override
    public List<NewsDto.Response> findAllNewsSummary(NewsSortType sortType) {
        List<News> newsList = newsRepository.findAll();
        return convertToResponseList(newsList, sortType);
    }

    @Override
    public Page<NewsDto.Response> findAllNews(NewsSortType sortType, Pageable pageable) {
        Page<News> newsPage;
        if (sortType == NewsSortType.LATEST) {
            Pageable latestPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            newsPage = newsRepository.findAllOrderByPublishedAtDescIdDesc(latestPageable);
        } else {
            newsPage = newsRepository.findAll(pageable);
        }
        List<NewsDto.Response> responses = convertToResponseList(newsPage.getContent(), sortType);
        return new PageImpl<>(responses, pageable, newsPage.getTotalElements());
    }

    private List<NewsDto.Response> convertToResponseList(List<News> newsList, NewsSortType sortType) {
        List<Long> newsIds = newsList.stream()
                .map(News::getId)
                .toList();

        Map<Long, Long> likeCountMap = newsList.stream()
                .collect(Collectors.toMap(
                        News::getId,
                        news -> newsLikeRepository.countByNewsId(news.getId())));

        Map<Long, Long> discussionCountMap = newsDiscussionPort.getDiscussionCounts(newsIds);

        Comparator<News> comparator;
        if (sortType == NewsSortType.POPULAR) {
            comparator = Comparator.comparing(
                            (News news) -> likeCountMap.getOrDefault(news.getId(), 0L))
                    .reversed();
        } else {
            comparator = Comparator
                    .comparing(News::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(News::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        }

        return newsList.stream()
                .sorted(comparator)
                .map(news -> new NewsDto.Response(
                        news,
                        likeCountMap.getOrDefault(news.getId(), 0L),
                        discussionCountMap.getOrDefault(news.getId(), news.getDiscussionCount()),
                        0L
                ))
                .toList();
    }

    @Override
    public NewsDto.DetailResponse findNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new DomainException(NewsErrorCode.NEWS_NOT_FOUND));

        long likeCount = newsLikeRepository.countByNewsId(id);
        long discussionCount = newsDiscussionPort.getDiscussionCount(id);

        return new NewsDto.DetailResponse(
                news,
                likeCount,
                discussionCount,
                0L // 공유 수는 아직 별도 집계가 없으므로 0으로 반환
        );
    }

    @Override
    public List<NewsDto.KeywordTrendResponse> findDailyTopKeywords() {
        Page<News> recentNewsPage = newsRepository.findAllOrderByPublishedAtDescIdDesc(
                PageRequest.of(0, RECENT_NEWS_WINDOW_SIZE));
        List<News> newsList = recentNewsPage.getContent();

        Map<NewsKeyword, Long> counts = newsList.stream()
                .filter(news -> news.getKeyword() != null)
                .collect(Collectors.groupingBy(News::getKeyword, Collectors.counting()));

        List<NewsDto.KeywordTrendResponse> responses = new ArrayList<>(counts.entrySet().stream()
                .sorted(Map.Entry.<NewsKeyword, Long>comparingByValue()
                        .reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .limit(DAILY_KEYWORD_LIMIT)
                .map(entry -> new NewsDto.KeywordTrendResponse(entry.getKey(), entry.getValue()))
                .toList());

        if (responses.size() >= DAILY_KEYWORD_LIMIT) {
            return responses;
        }

        for (NewsKeyword fallbackKeyword : KEYWORD_FALLBACK_ORDER) {
            boolean alreadyPresent = responses.stream()
                    .anyMatch(response -> response.getKeyword() == fallbackKeyword);
            if (!alreadyPresent) {
                responses.add(new NewsDto.KeywordTrendResponse(fallbackKeyword, 0L));
            }
            if (responses.size() >= DAILY_KEYWORD_LIMIT) {
                break;
            }
        }

        return responses;
    }
}
