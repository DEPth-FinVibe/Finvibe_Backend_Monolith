package depth.finvibe.modules.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.BatchUpdatePrice;
import depth.finvibe.modules.market.domain.Category;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.CategoryDto;
import depth.finvibe.modules.market.dto.CategoryInternalDto;
import depth.finvibe.common.error.DomainException;

@Service
@RequiredArgsConstructor
public class CategoryQueryService implements CategoryQueryUseCase {

    private final CategoryRepository categoryRepository;
    private final StockRepository stockRepository;
    private final BatchUpdatePriceRepository batchUpdatePriceRepository;

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
    @Transactional(readOnly = true)
    public CategoryDto.ChangeRateResponse getCategoryChangeRate(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException(MarketErrorCode.CATEGORY_NOT_FOUND));

        List<Stock> stocks = stockRepository.findByCategoryId(categoryId);
        if (stocks.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_STOCKS_IN_CATEGORY);
        }

        List<Long> stockIds = stocks.stream().map(Stock::getId).toList();
        List<BatchUpdatePrice> batchPrices = batchUpdatePriceRepository.findByStockIds(stockIds);
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
        List<BatchUpdatePrice> batchPrices = batchUpdatePriceRepository.findByStockIds(stockIds);
        if (batchPrices.isEmpty()) {
            throw new DomainException(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
        }

        Map<Long, BatchUpdatePrice> priceByStockId = batchPrices.stream()
                .collect(Collectors.toMap(BatchUpdatePrice::getStockId, price -> price, (first, second) -> first));

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

    private record StockWithPrice(Stock stock, BatchUpdatePrice batchUpdatePrice) {
        private BigDecimal value() {
            if (batchUpdatePrice == null || batchUpdatePrice.getValue() == null) {
                return BigDecimal.ZERO;
            }
            return batchUpdatePrice.getValue();
        }
    }
}
