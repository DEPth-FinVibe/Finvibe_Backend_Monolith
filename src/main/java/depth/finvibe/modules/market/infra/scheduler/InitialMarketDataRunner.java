package depth.finvibe.modules.market.infra.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.in.CategoryCommandUseCase;
import depth.finvibe.modules.market.application.port.in.StockCommandUseCase;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.application.port.out.StockThemeRepository;
import depth.finvibe.modules.market.domain.Category;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "market.init", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InitialMarketDataRunner implements CommandLineRunner {

    private static final String LOCK_NAME = "initial-market-data-runner";
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(10);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofSeconds(5);
    private static final String FALLBACK_CATEGORY_NAME = "기타";

    private final LockProvider lockProvider;
    private final CategoryCommandUseCase categoryCommandUseCase;
    private final StockCommandUseCase stockCommandUseCase;
    private final StockRepository stockRepository;
    private final StockThemeRepository stockThemeRepository;

    @Override
    public void run(String... args) {
        LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
        executor.executeWithLock((Runnable) this::initializeMarketData,
                new LockConfiguration(Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR));
    }

    private void initializeMarketData() {
        log.info("최소 초기화 배치를 시작합니다. 대상: category seed, stock master seed");

        if (!categoryCommandUseCase.existsAny()) {
            List<Category> categories = loadCategoryThemes().stream()
                    .map(theme -> Category.builder()
                            .name(theme)
                            .build())
                    .toList();
            categoryCommandUseCase.bulkInsert(categories);
            log.info("카테고리 초기 시드 적재 완료 - count={}", categories.size());
        }

        if (!stockRepository.existsAny()) {
            log.info("종목 마스터 데이터가 없어 최초 적재를 실행합니다.");

            stockCommandUseCase.bulkUpsertStocks();
            log.info("종목 마스터 초기 적재 완료");
        } else {
            log.info("종목 마스터 데이터가 이미 존재해 초기 적재를 건너뜁니다.");
        }
        log.info("최소 초기화 배치가 완료되었습니다.");
    }

    private List<String> loadCategoryThemes() {
        Set<String> themes = stockThemeRepository.findSymbolToThemeMap().values().stream()
                .map(theme -> theme == null ? "" : theme.trim())
                .filter(theme -> !theme.isBlank())
                .collect(Collectors.toSet());
        themes.add(FALLBACK_CATEGORY_NAME);
        return themes.stream()
                .sorted()
                .toList();
    }
}
