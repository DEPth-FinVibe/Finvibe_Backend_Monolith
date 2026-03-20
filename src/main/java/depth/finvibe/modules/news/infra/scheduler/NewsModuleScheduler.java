package depth.finvibe.modules.news.infra.scheduler;

import depth.finvibe.modules.news.application.port.in.NewsCommandUseCase;
import depth.finvibe.modules.news.application.port.in.ThemeCommandUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsModuleScheduler {

    private final NewsCommandUseCase newsCommandUseCase;
    private final ThemeCommandUseCase themeCommandUseCase;

    /**
     * 지정된 스케줄에 맞춰 최신 뉴스를 수집하고 AI 분석을 수행합니다.
     */
    public void syncLatestNews() {
        log.info("Starting scheduled news collection and analysis...");
        newsCommandUseCase.syncLatestNews();
        themeCommandUseCase.generateTodayThemes();
        log.info("Finished scheduled news collection and analysis.");
    }

    /**
     * 3시간마다 모든 뉴스의 토론 수를 최신화합니다.
     */
    public void syncDiscussionCounts() {
        log.info("Starting periodic discussion count synchronization...");
        newsCommandUseCase.syncAllDiscussionCounts();
        log.info("Finished periodic discussion count synchronization.");
    }
}
