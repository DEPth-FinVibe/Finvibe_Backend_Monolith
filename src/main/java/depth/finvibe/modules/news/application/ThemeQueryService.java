package depth.finvibe.modules.news.application;

import depth.finvibe.modules.news.application.port.out.NewsRepository;
import depth.finvibe.modules.news.application.port.out.MarketCategoryChangeRatePort;
import depth.finvibe.modules.news.application.port.in.ThemeQueryUseCase;
import depth.finvibe.modules.news.application.port.out.ThemeDailyRepository;
import depth.finvibe.modules.news.domain.ThemeDaily;
import depth.finvibe.modules.news.domain.error.ThemeErrorCode;
import depth.finvibe.modules.news.dto.ThemeDto;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThemeQueryService implements ThemeQueryUseCase {

    private final ThemeDailyRepository themeDailyRepository;
    private final NewsRepository newsRepository;
    private final MarketCategoryChangeRatePort marketCategoryChangeRatePort;

    @Override
    public List<ThemeDto.SummaryResponse> findTodayThemes() {
        return themeDailyRepository.findDistinctCategoryIds().stream()
                .map(themeDailyRepository::findLatestByCategoryId)
                .flatMap(Optional::stream)
                .map(themeDaily -> new ThemeDto.SummaryResponse(
                        themeDaily,
                        marketCategoryChangeRatePort.fetchAverageChangePct(
                                themeDaily.getCategoryId())))
                .toList();
    }

    @Override
    public ThemeDto.DetailResponse findTodayThemeDetail(Long categoryId) {
        ThemeDaily themeDaily = themeDailyRepository.findLatestByCategoryId(categoryId)
                .orElseThrow(() -> new DomainException(ThemeErrorCode.THEME_NOT_FOUND));

        LocalDateTime themeDateStart = themeDaily.getThemeDate().atStartOfDay();
        LocalDateTime themeDateEnd = themeDateStart.plusDays(1).minusNanos(1);
        List<ThemeDto.NewsSummary> news = newsRepository
                .findAllByCategoryIdAndPublishedAtBetweenOrderByPublishedAtDesc(categoryId, themeDateStart, themeDateEnd)
                .stream()
                .map(ThemeDto.NewsSummary::new)
                .toList();

        return new ThemeDto.DetailResponse(themeDaily, news);
    }
}
