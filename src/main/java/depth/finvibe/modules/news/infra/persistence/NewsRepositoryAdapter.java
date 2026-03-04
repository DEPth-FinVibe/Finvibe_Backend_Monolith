package depth.finvibe.modules.news.infra.persistence;

import depth.finvibe.modules.news.application.port.out.NewsRepository;
import depth.finvibe.modules.news.domain.News;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NewsRepositoryAdapter implements NewsRepository {

    private final NewsJpaRepository newsJpaRepository;

    @Override
    public News save(News news) {
        return newsJpaRepository.save(news);
    }

    @Override
    public List<News> findAll() {
        return newsJpaRepository.findAll();
    }

    @Override
    public Optional<News> findById(Long id) {
        return newsJpaRepository.findById(id);
    }

    @Override
    public boolean existsByTitle(String title) {
        return newsJpaRepository.existsByTitle(title);
    }

    @Override
    public org.springframework.data.domain.Page<News> findAll(org.springframework.data.domain.Pageable pageable) {
        return newsJpaRepository.findAll(pageable);
    }

    @Override
    public org.springframework.data.domain.Page<News> findAllOrderByPublishedAtDescIdDesc(
            org.springframework.data.domain.Pageable pageable) {
        return newsJpaRepository.findAllByOrderByPublishedAtDescIdDesc(pageable);
    }

    @Override
    public List<News> findAllByCreatedAtAfter(LocalDateTime createdAfter) {
        return newsJpaRepository.findAllByCreatedAtAfter(createdAfter);
    }

    @Override
    public List<News> findAllByCategoryIdAndPublishedAtBetweenOrderByPublishedAtDesc(
            Long categoryId,
            LocalDateTime start,
            LocalDateTime end) {
        return newsJpaRepository.findAllByCategoryIdAndPublishedAtBetweenOrderByPublishedAtDesc(
                categoryId,
                start,
                end);
    }

    @Override
    public List<NewsCategoryCount> countByCategoryIdForPeriod(LocalDateTime start, LocalDateTime end) {
        return newsJpaRepository.countByCategoryIdForPeriod(start, end).stream()
                .map(row -> new NewsCategoryCount(row.getCategoryId(), row.getCount()))
                .toList();
    }
}
