package depth.finvibe.modules.news.infra.persistence;

import depth.finvibe.modules.news.domain.ThemeDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ThemeDailyJpaRepository extends JpaRepository<ThemeDaily, Long> {
    List<ThemeDaily> findAllByThemeDate(LocalDate themeDate);

    Optional<ThemeDaily> findByThemeDateAndCategoryId(LocalDate themeDate, Long categoryId);

    @Query("""
            select distinct t.categoryId
            from ThemeDaily t
            where t.categoryId is not null
            order by t.categoryId asc
            """)
    List<Long> findDistinctCategoryIds();

    Optional<ThemeDaily> findTopByCategoryIdOrderByCreatedAtDescIdDesc(Long categoryId);

    void deleteAllByThemeDate(LocalDate themeDate);
}
