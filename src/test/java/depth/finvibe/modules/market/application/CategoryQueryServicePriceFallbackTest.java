package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import depth.finvibe.common.error.DomainException;
import depth.finvibe.modules.market.application.port.out.BatchUpdatePriceRepository;
import depth.finvibe.modules.market.application.port.out.CategoryRepository;
import depth.finvibe.modules.market.application.port.out.PriceCandleRepository;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.BatchUpdatePrice;
import depth.finvibe.modules.market.domain.Category;
import depth.finvibe.modules.market.domain.PriceCandle;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.modules.market.dto.CategoryDto;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryQueryServicePriceFallbackTest {

    private static final Long CATEGORY_ID = 8L;
    private static final Long STOCK_ID = 4971L;
    private static final LocalDateTime LAST_TRADING_AT = LocalDateTime.of(2026, 8, 28, 0, 0);

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private BatchUpdatePriceRepository batchUpdatePriceRepository;
    @Mock
    private PriceCandleRepository priceCandleRepository;

    private CategoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryQueryService(
                categoryRepository,
                stockRepository,
                batchUpdatePriceRepository,
                priceCandleRepository
        );

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(stockRepository.findByCategoryId(CATEGORY_ID)).thenReturn(List.of(stock()));
    }

    @Test
    @DisplayName("현재가 캐시가 비어도 최신 일봉 종가로 종목 목록을 응답한다")
    void getCategoryStocks_emptyBatchCache_fallsBackToDailyCandle() {
        when(batchUpdatePriceRepository.findByStockIds(anyList())).thenReturn(List.of());
        when(priceCandleRepository.findLatestByStockIdsAndTimeframe(List.of(STOCK_ID), Timeframe.DAY))
                .thenReturn(List.of(dailyCandle()));

        CategoryDto.StockListResponse response = service.getCategoryStocksByValue(CATEGORY_ID);

        assertThat(response.getStocks()).hasSize(1);
        assertThat(response.getCategoryId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    @DisplayName("현재가 캐시가 비어도 최신 일봉으로 등락률을 계산한다")
    void getCategoryChangeRate_emptyBatchCache_fallsBackToDailyCandle() {
        when(batchUpdatePriceRepository.findByStockIds(anyList())).thenReturn(List.of());
        when(priceCandleRepository.findLatestByStockIdsAndTimeframe(List.of(STOCK_ID), Timeframe.DAY))
                .thenReturn(List.of(dailyCandle()));

        CategoryDto.ChangeRateResponse response = service.getCategoryChangeRate(CATEGORY_ID);

        assertThat(response.getAverageChangePct()).isEqualByComparingTo("1.2500");
        assertThat(response.getStockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재가 캐시가 있으면 일봉을 조회하지 않는다")
    void getCategoryStocks_cacheHit_skipsCandleLookup() {
        when(batchUpdatePriceRepository.findByStockIds(anyList())).thenReturn(List.of(batchPrice()));

        CategoryDto.StockListResponse response = service.getCategoryStocksByValue(CATEGORY_ID);

        assertThat(response.getStocks()).hasSize(1);
        verify(priceCandleRepository, never()).findLatestByStockIdsAndTimeframe(any(), any());
    }

    @Test
    @DisplayName("현재가도 일봉도 없으면 그때는 오류를 낸다")
    void getCategoryStocks_noPriceAnywhere_throws() {
        when(batchUpdatePriceRepository.findByStockIds(anyList())).thenReturn(List.of());
        when(priceCandleRepository.findLatestByStockIdsAndTimeframe(anyList(), eq(Timeframe.DAY)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getCategoryStocksByValue(CATEGORY_ID))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode())
                .isEqualTo(MarketErrorCode.NO_PRICE_DATA_AVAILABLE);
    }

    @Test
    @DisplayName("종목 수가 많은 카테고리는 일봉 폴백을 시도하지 않는다")
    void getCategoryStocks_tooManyStocks_skipsFallback() {
        List<Stock> manyStocks = java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(id -> Stock.builder().id(id).symbol("00000" + id).name("종목" + id).categoryId(CATEGORY_ID).build())
                .toList();
        when(stockRepository.findByCategoryId(CATEGORY_ID)).thenReturn(manyStocks);
        when(batchUpdatePriceRepository.findByStockIds(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.getCategoryStocksByValue(CATEGORY_ID))
                .isInstanceOf(DomainException.class);
        verify(priceCandleRepository, never()).findLatestByStockIdsAndTimeframe(any(), any());
    }

    private Category category() {
        return Category.builder().id(CATEGORY_ID).name("반도체").build();
    }

    private Stock stock() {
        return Stock.builder().id(STOCK_ID).symbol("000660").name("SK하이닉스").categoryId(CATEGORY_ID).build();
    }

    private PriceCandle dailyCandle() {
        return PriceCandle.create(
                STOCK_ID,
                Timeframe.DAY,
                LAST_TRADING_AT,
                new BigDecimal("70000"),
                new BigDecimal("71000"),
                new BigDecimal("69000"),
                new BigDecimal("70500"),
                new BigDecimal("1.25"),
                new BigDecimal("1000"),
                new BigDecimal("70500000")
        );
    }

    private BatchUpdatePrice batchPrice() {
        return BatchUpdatePrice.builder()
                .stockId(STOCK_ID)
                .at(LAST_TRADING_AT)
                .price(new BigDecimal("70500"))
                .open(new BigDecimal("70000"))
                .high(new BigDecimal("71000"))
                .low(new BigDecimal("69000"))
                .prevDayChangePct(new BigDecimal("1.25"))
                .volume(new BigDecimal("1000"))
                .value(new BigDecimal("70500000"))
                .build();
    }
}
