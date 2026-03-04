package depth.finvibe.modules.news.application.port.in;

import depth.finvibe.modules.news.dto.ThemeDto;

import java.util.List;

public interface ThemeQueryUseCase {
    List<ThemeDto.SummaryResponse> findTodayThemes();

    ThemeDto.DetailResponse findTodayThemeDetail(Long categoryId);
}
