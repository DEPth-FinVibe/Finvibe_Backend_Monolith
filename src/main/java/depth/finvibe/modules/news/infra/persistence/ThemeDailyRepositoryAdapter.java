package depth.finvibe.modules.news.infra.persistence;

import depth.finvibe.modules.news.application.port.out.ThemeDailyRepository;
import depth.finvibe.modules.news.domain.ThemeDaily;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ThemeDailyRepositoryAdapter implements ThemeDailyRepository {

    private final ThemeDailyJpaRepository themeDailyJpaRepository;

    @Override
    public ThemeDaily save(ThemeDaily themeDaily) {
        return themeDailyJpaRepository.save(themeDaily);
    }

    @Override
    public List<ThemeDaily> findAllByThemeDate(LocalDate themeDate) {
        return themeDailyJpaRepository.findAllByThemeDate(themeDate);
    }

    @Override
    public Optional<ThemeDaily> findByThemeDateAndCategoryId(LocalDate themeDate, Long categoryId) {
        return themeDailyJpaRepository.findByThemeDateAndCategoryId(themeDate, categoryId);
    }

    @Override
    public List<Long> findDistinctCategoryIds() {
        return themeDailyJpaRepository.findDistinctCategoryIds();
    }

    @Override
    public Optional<ThemeDaily> findLatestByCategoryId(Long categoryId) {
        return themeDailyJpaRepository.findTopByCategoryIdOrderByCreatedAtDescIdDesc(categoryId);
    }

    @Override
    public void deleteAllByThemeDate(LocalDate themeDate) {
        themeDailyJpaRepository.deleteAllByThemeDate(themeDate);
    }
}
