package depth.finvibe.modules.news.application.port.in;

import depth.finvibe.modules.news.dto.NewsDto;
import depth.finvibe.modules.news.dto.NewsSortType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NewsQueryUseCase {
    List<NewsDto.Response> findAllNewsSummary(NewsSortType sortType);

    Page<NewsDto.Response> findAllNews(NewsSortType sortType, Pageable pageable);

    NewsDto.DetailResponse findNewsById(Long id);

    List<NewsDto.KeywordTrendResponse> findDailyTopKeywords();
}
