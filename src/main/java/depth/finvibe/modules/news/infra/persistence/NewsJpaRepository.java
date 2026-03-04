package depth.finvibe.modules.news.infra.persistence;

import depth.finvibe.modules.news.domain.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsJpaRepository extends JpaRepository<News, Long> {
    boolean existsByTitle(String title);

    org.springframework.data.domain.Page<News> findAllByOrderByPublishedAtDescIdDesc(
            org.springframework.data.domain.Pageable pageable);

    List<News> findAllByCreatedAtAfter(LocalDateTime createdAfter);

    List<News> findAllByCategoryIdAndPublishedAtBetweenOrderByPublishedAtDesc(
            Long categoryId,
            LocalDateTime start,
            LocalDateTime end);

    @Query("""
            select n.categoryId as categoryId, count(n.id) as count
            from News n
            where n.publishedAt between :start and :end
              and n.categoryId is not null
            group by n.categoryId
            order by count desc
            """)
    List<CategoryCountProjection> countByCategoryIdForPeriod(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    interface CategoryCountProjection {
        Long getCategoryId();

        long getCount();
    }
}
