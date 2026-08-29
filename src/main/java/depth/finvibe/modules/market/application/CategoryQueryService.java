package depth.finvibe.modules.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.market.application.port.in.CategoryQueryUseCase;
import depth.finvibe.modules.market.application.port.out.BatchUpdatePriceRepository;
import depth.finvibe.modules.market.application.port.out.CategoryRepository;
import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.BatchUpdatePrice;
import depth.finvibe.modules.market.domain.Category;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.CategoryDto;
import depth.finvibe.modules.market.dto.PriceCandleDto;
import depth.finvibe.modules.market.dto.CategoryInternalDto;
import depth.finvibe.common.error.DomainException;

@Service
@RequiredArgsConstructor
public class CategoryQueryService implements CategoryQueryUseCase {

    /**
     * 일봉 폴백을 시도할 최대 종목 수.
     * "기타"처럼 수천 종목짜리 카테고리에서 종목별 최신 캔들을 훑지 않도록 막는다.
     */
    private static final int MAX_CANDLE_FALLBACK_STOCKS = 500;

    private final CategoryRepository categoryRepository;
    private final StockRepository stockRepository;
    private final BatchUpdatePriceRepository batchUpdatePriceRepository;
    private final PriceCandleRepository priceCandleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto.Response> getAllCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(category -> CategoryDto.Response.of(
                        category,
                        stockRepository.countByCategoryId(category.getId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryInternalDto.Response> getAllCategoriesForInternal() {
        return categoryRepository.findAll().stream()
                .map(CategoryInternalDto.Response::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public CategoryDto.ChangeRateResponse getCategoryChangeRate(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException(MarketErrorCode.CATEGORY_NOT_FOUND));

        List<Stock> stocks = stockRepository.findByCategoryId(categoryId);
        if (stocks.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_STOCKS_IN_CATEGORY);
        }

        List<Long> stockIds = stocks.stream().map(Stock::getId).toList();
        List<BatchUpdatePrice> batchPrices = List.copyOf(resolvePrices(stockIds).values());
        if (batchPrices.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
        }

        List<BatchUpdatePrice> pricesWithChange = batchPrices.stream()
                .filter(price -> price.getPrevDayChangePct() != null)
                .toList();
        if (pricesWithChange.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
        }

        BigDecimal sum = pricesWithChange.stream()
                .map(BatchUpdatePrice::getPrevDayChangePct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = sum.divide(
                BigDecimal.valueOf(pricesWithChange.size()),
                4,
                RoundingMode.HALF_UP
        );

        int positiveCount = (int) pricesWithChange.stream()
                .filter(price -> price.getPrevDayChangePct().compareTo(BigDecimal.ZERO) > 0)
                .count();
        int negativeCount = (int) pricesWithChange.stream()
                .filter(price -> price.getPrevDayChangePct().compareTo(BigDecimal.ZERO) < 0)
                .count();

        LocalDateTime updatedAt = batchPrices.stream()
                .map(BatchUpdatePrice::getAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return CategoryDto.ChangeRateResponse.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .averageChangePct(average)
                .stockCount(pricesWithChange.size())
                .positiveCount(positiveCount)
                .negativeCount(negativeCount)
                .updatedAt(updatedAt)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDto.StockListResponse getCategoryStocksByValue(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException(MarketErrorCode.CATEGORY_NOT_FOUND));

        List<Stock> stocks = stockRepository.findByCategoryId(categoryId);
        if (stocks.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_STOCKS_IN_CATEGORY);
        }

        List<Long> stockIds = stocks.stream().map(Stock::getId).toList();
        Map<Long, BatchUpdatePrice> priceByStockId = resolvePrices(stockIds);

        List<StockWithPrice> stockWithPrices = stocks.stream()
                .map(stock -> new StockWithPrice(stock, priceByStockId.get(stock.getId())))
                .filter(item -> item.batchUpdatePrice() != null)
                .toList();

        if (stockWithPrices.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
        }

        List<StockWithPrice> sorted = stockWithPrices.stream()
                .sorted(Comparator.comparing(StockWithPrice::value).reversed()
                        .thenComparing(item -> item.stock().getId()))
                .toList();

        int rank = 1;
        List<CategoryDto.StockValueResponse> responses = new ArrayList<>();
        for (StockWithPrice item : sorted) {
            responses.add(CategoryDto.StockValueResponse.of(item.stock(), item.batchUpdatePrice(), rank++));
        }

        return CategoryDto.StockListResponse.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .stocks(responses)
                .build();
    }

    /**
     * 카테고리 종목의 가격을 확보한다.
     * <p>
     * 현재가 배치 캐시(Redis)를 우선 쓰되, 비어 있거나 일부만 덮으면 최신 일봉 종가로 메운다.
     * 배치 캐시는 TTL이 있는데 장중에만 갱신돼서 주말·연휴처럼 장이 오래 닫히면 통째로 비는데,
     * 그때도 화면이 죽지 않도록 종가로 대체한다. 장이 닫혀 있으면 종가가 곧 정답이기도 하다.
     */
    private Map<Long, BatchUpdatePrice> resolvePrices(List<Long> stockIds) {
        Map<Long, BatchUpdatePrice> priceByStockId = batchUpdatePriceRepository.findByStockIds(stockIds).stream()
                .collect(Collectors.toMap(
                        BatchUpdatePrice::getStockId,
                        price -> price,
                        (first, second) -> first,
                        HashMap::new
                ));

        List<Long> uncoveredStockIds = stockIds.stream()
                .filter(stockId -> !priceByStockId.containsKey(stockId))
                .toList();

        if (uncoveredStockIds.isEmpty() || uncoveredStockIds.size() > MAX_CANDLE_FALLBACK_STOCKS) {
            return priceByStockId;
        }

        priceCandleRepository.findLatestByStockIdsAndTimeframe(uncoveredStockIds, Timeframe.DAY).stream()
                .map(candle -> BatchUpdatePrice.from(PriceCandleDto.Response.from(candle)))
                .forEach(price -> priceByStockId.putIfAbsent(price.getStockId(), price));

        return priceByStockId;
    }

    private record StockWithPrice(Stock stock, BatchUpdatePrice batchUpdatePrice) {
        private BigDecimal value() {
            if (batchUpdatePrice == null || batchUpdatePrice.getValue() == null) {
                return BigDecimal.ZERO;
            }
            return batchUpdatePrice.getValue();
        }
    }
}
