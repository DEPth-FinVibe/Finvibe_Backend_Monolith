package depth.finvibe.modules.news.application.port.out;

import depth.finvibe.modules.news.domain.ThemeDaily;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ThemeDailyRepository {
    ThemeDaily save(ThemeDaily themeDaily);

    List<ThemeDaily> findAllByThemeDate(LocalDate themeDate);

    Optional<ThemeDaily> findByThemeDateAndCategoryId(LocalDate themeDate, Long categoryId);

    List<Long> findDistinctCategoryIds();

    Optional<ThemeDaily> findLatestByCategoryId(Long categoryId);

    void deleteAllByThemeDate(LocalDate themeDate);
}
