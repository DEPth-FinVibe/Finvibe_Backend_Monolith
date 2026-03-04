package depth.finvibe.modules.news.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import depth.finvibe.modules.news.domain.News;

public interface NewsRepository {
    News save(News news);

    List<News> findAll();

    Optional<News> findById(Long id);

    boolean existsByTitle(String title);

    org.springframework.data.domain.Page<News> findAll(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<News> findAllOrderByPublishedAtDescIdDesc(
            org.springframework.data.domain.Pageable pageable);

    List<News> findAllByCreatedAtAfter(LocalDateTime createdAfter);

    List<News> findAllByCategoryIdAndPublishedAtBetweenOrderByPublishedAtDesc(
            Long categoryId,
            LocalDateTime start,
            LocalDateTime end);

    List<NewsCategoryCount> countByCategoryIdForPeriod(LocalDateTime start, LocalDateTime end);

    record NewsCategoryCount(Long categoryId, long count) {
    }
}
