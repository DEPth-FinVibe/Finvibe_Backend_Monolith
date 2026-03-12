package depth.finvibe.modules.market.infra.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
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
import depth.finvibe.modules.market.application.BatchPriceUpdateService;
import depth.finvibe.modules.market.application.HolidayCalendarService;
import depth.finvibe.modules.market.application.IndexMinuteCandleCacheService;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.application.port.out.StockThemeRepository;
import depth.finvibe.modules.market.domain.Category;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.enums.MarketIndexType;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.error.GlobalErrorCode;

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
    private final BatchPriceUpdateService batchPriceUpdateService;
    private final IndexMinuteCandleCacheService indexMinuteCandleCacheService;
    private final HolidayCalendarService holidayCalendarService;
    private final StockRepository stockRepository;
    private final StockThemeRepository stockThemeRepository;

    @Override
    public void run(String... args) {
        LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
        executor.executeWithLock((Runnable) this::initializeMarketData,
                new LockConfiguration(Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR));
    }

    private void initializeMarketData() {
        if (!categoryCommandUseCase.existsAny()) {
            List<Category> categories = loadCategoryThemes().stream()
                    .map(theme -> Category.builder()
                            .name(theme)
                            .build())
                    .toList();
            categoryCommandUseCase.bulkInsert(categories);
        }

        boolean stocksJustLoaded = false;
        if (!stockRepository.existsAny()) {
            log.info("어플리케이션 초기화 작업을 위해 주식 데이터를 최초로 적재합니다.");

            stockCommandUseCase.bulkUpsertStocks();
            stockCommandUseCase.renewStockCharts();
            stocksJustLoaded = true;
        }

        if (!stocksJustLoaded) {
            runBatchPriceUpdateIfClosedAndMissing();
        } else {
            log.info("주식 데이터를 이번 시작에서 최초 적재했으므로 배치 시세 보정을 건너뜁니다.");
        }

        initializeIndexMinuteCandlesIfMissing();

        initializeHolidayCalendarIfMissing();
    }

    /**
     * 당월·다음 달 휴장일 달력이 없으면 KIS 국내휴장일조회로 적재.
     */
    private void initializeHolidayCalendarIfMissing() {
        YearMonth now = YearMonth.now();
        try {
            holidayCalendarService.ensureCalendarForMonth(now);
            holidayCalendarService.ensureCalendarForMonth(now.plusMonths(1));
        } catch (DomainException ex) {
            if (ex.getErrorCode() == GlobalErrorCode.CIRCUIT_BREAKER_OPEN) {
                log.warn("KIS API Circuit Breaker 열림으로 휴장일 달력 초기화 스킵.");
            } else {
                log.error("휴장일 달력 초기화 중 도메인 에러 발생.", ex);
            }
        } catch (Exception ex) {
            log.error("휴장일 달력 초기화 중 예상치 못한 에러 발생.", ex);
        }
    }

    private void runBatchPriceUpdateIfClosedAndMissing() {
        if (MarketHours.getCurrentStatus() == MarketStatus.OPEN) {
            log.info("장이 열려 있어 시작 시 배치 시세 보정은 건너뜁니다.");
            return;
        }

        if (!batchPriceUpdateService.hasMissingBatchPricesByKey()) {
            log.info("장이 닫혀 있고 누락된 배치 시세 키가 없어 시작 시 보정을 건너뜁니다.");
            return;
        }

        log.info("장이 닫혀 있고 누락된 배치 시세 키가 있어 시작 시 배치 시세 보정을 실행합니다.");
        batchPriceUpdateService.updateHoldingStockPrices();
    }

    /**
     * 지수 분봉 데이터가 없으면 KIS API로부터 최신 데이터를 가져와 초기화
     * KIS API는 최신 약 2시간의 분봉 데이터만 제공하므로, 완전한 과거 데이터는 불가
     */
    private void initializeIndexMinuteCandlesIfMissing() {
        log.info("지수 분봉 데이터 초기화를 시작합니다.");

        for (MarketIndexType indexType : MarketIndexType.values()) {
            try {
                indexMinuteCandleCacheService.initializeIndexMinuteCandlesIfEmpty(indexType);
                log.info("지수 분봉 초기화 완료. indexType={}", indexType);
            } catch (DomainException ex) {
                if (ex.getErrorCode() == GlobalErrorCode.CIRCUIT_BREAKER_OPEN) {
                    log.warn("KIS API Circuit Breaker 열림으로 지수 분봉 초기화 스킵. indexType={}",
                            indexType);
                } else {
                    log.error("지수 분봉 초기화 중 도메인 에러 발생. indexType={}", indexType, ex);
                }
            } catch (Exception ex) {
                log.error("지수 분봉 초기화 중 예상치 못한 에러 발생. indexType={}", indexType, ex);
            }
        }
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
