package depth.finvibe.modules.market.application;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.market.application.port.in.StockCommandUseCase;
import depth.finvibe.modules.market.application.port.out.CategoryRepository;
import depth.finvibe.modules.market.application.port.out.RealMarketClient;
import depth.finvibe.modules.market.application.port.out.StockRankingRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.application.port.out.StockThemeRepository;
import depth.finvibe.modules.market.domain.Category;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.StockRanking;
import depth.finvibe.modules.market.domain.enums.RankType;
import depth.finvibe.modules.market.dto.StockDto;

@Slf4j
@RequiredArgsConstructor
@Service
public class StockService implements StockCommandUseCase {

    private static final String FALLBACK_CATEGORY_NAME = "기타";

    private final StockRepository stockRepository;
    private final CategoryRepository categoryRepository;
    private final StockThemeRepository stockThemeRepository;
    private final RealMarketClient realMarketClient;
    private final StockRankingRepository stockRankingRepository;

    @Override
    @Transactional
    public void bulkUpsertStocks() {
        List<StockDto.RealMarketStockResponse> stocksInKOSPI = realMarketClient.fetchStocksInRealMarket();

        List<Stock> stocksToUpsert = convertToStocksFrom(stocksInKOSPI);

        stockRepository.bulkUpsertStocks(stocksToUpsert);
    }

    private List<Stock> convertToStocksFrom(List<StockDto.RealMarketStockResponse> stocksInKOSPI) {
        List<Category> allCategories = categoryRepository.findAll();
        Map<String, Category> categoryByName = allCategories.stream()
                .collect(Collectors.toMap(Category::getName, category -> category, (existing, replacement) -> existing));
        Map<String, String> symbolToThemeMap = stockThemeRepository.findSymbolToThemeMap();

        return stocksInKOSPI.stream()
                .map(res -> {
                    String theme = symbolToThemeMap.get(res.getSymbol());
                    Category category = resolveCategory(categoryByName, theme);
                    return createStockFrom(res, category);
                })
                .toList();
    }

    private Stock createStockFrom(StockDto.RealMarketStockResponse res, Category category) {
        return Stock.builder()
                .symbol(res.getSymbol())
                .name(res.getName())
                .categoryId(category.getId())
                .build();
    }

    private Category resolveCategory(Map<String, Category> categoryByName, String theme) {
        if (theme != null && !theme.isBlank()) {
            Category matched = categoryByName.get(theme);
            if (matched != null) {
                return matched;
            }
        }
        Category fallback = categoryByName.get(FALLBACK_CATEGORY_NAME);
        if (fallback == null) {
            throw new IllegalStateException("Fallback category not found");
        }
        return fallback;
    }

    @Override
    @Transactional
    public void renewStockCharts() {
        List<StockDto.RankingResponse> rankingResponses = realMarketClient.fetchStockRankings();

        List<String> symbols = rankingResponses.stream()
                .map(StockDto.RankingResponse::getSymbol)
                .toList();
        List<Stock> stocks = stockRepository.findAllBySymbolIn(symbols);
        Map<String, Long> symbolToStockIdMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getSymbol, Stock::getId));

        List<RankType> rankTypes = rankingResponses.stream()
                .map(StockDto.RankingResponse::getRankType)
                .distinct()
                .toList();
        stockRankingRepository.deleteByRankTypeIn(rankTypes);

        List<StockRanking> stockRankings = rankingResponses.stream()
                .filter(ranking -> symbolToStockIdMap.containsKey(ranking.getSymbol()))
                .map(ranking -> createStockRankingFrom(
                        ranking,
                        symbolToStockIdMap.get(ranking.getSymbol())
                ))
                .toList();

        List<StockRanking> deduplicatedStockRankings = deduplicateStockRankings(stockRankings);

        stockRankingRepository.bulkUpsertStockRankings(deduplicatedStockRankings);
    }

    private StockRanking createStockRankingFrom(StockDto.RankingResponse rankingResponse, Long stockId) {
        return StockRanking.builder()
                .stockId(stockId)
                .rankType(rankingResponse.getRankType())
                .rank(rankingResponse.getRank())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private List<StockRanking> deduplicateStockRankings(List<StockRanking> stockRankings) {
        Map<StockRankingKey, StockRanking> deduplicated = new LinkedHashMap<>();

        for (StockRanking stockRanking : stockRankings) {
            StockRankingKey key = new StockRankingKey(stockRanking.getStockId(), stockRanking.getRankType());
            deduplicated.merge(key, stockRanking, this::pickHigherPriorityRanking);
        }

        int duplicateCount = stockRankings.size() - deduplicated.size();
        if (duplicateCount > 0) {
            log.warn("중복 종목 순위 데이터를 {}건 제거했습니다.", duplicateCount);
        }

        return deduplicated.values().stream().toList();
    }

    private StockRanking pickHigherPriorityRanking(StockRanking existing, StockRanking candidate) {
        return candidate.getRank() < existing.getRank() ? candidate : existing;
    }

    private record StockRankingKey(Long stockId, RankType rankType) {
    }

}
