package depth.finvibe.modules.market.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import depth.finvibe.common.error.DomainException;
import depth.finvibe.common.error.GlobalErrorCode;
import depth.finvibe.modules.market.application.port.out.CandleFetchResult;
import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.domain.enums.Timeframe;
import depth.finvibe.modules.market.infra.client.dto.KisDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealMarketClientImplCandleTest {

    private static final Long STOCK_ID = 4971L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 5, 21, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 20, 0, 0);

    @Mock
    private KisApiClient kisApiClient;
    @Mock
    private StockRepository stockRepository;

    private RealMarketClientImpl client;

    @BeforeEach
    void setUp() {
        client = new RealMarketClientImpl(
                kisApiClient,
                List.of(),
                stockRepository,
                new SimpleMeterRegistry()
        );
        when(stockRepository.findById(STOCK_ID)).thenReturn(Optional.of(
                Stock.builder().id(STOCK_ID).symbol("005930").name("삼성전자").build()
        ));
    }

    @Test
    @DisplayName("일봉 KIS 호출 실패를 예외 대신 실패 결과로 반환한다")
    void fetchPriceCandles_dailyProviderFailure_returnsFailedResult() {
        when(kisApiClient.fetchDailyItemChartPrice(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN));

        CandleFetchResult result = client.fetchPriceCandles(STOCK_ID, START, END, Timeframe.DAY);

        assertThat(result.status()).isEqualTo(CandleFetchResult.Status.FAILED);
        assertThat(result.candles()).isEmpty();
    }

    @Test
    @DisplayName("성공 코드지만 일봉 데이터가 비어 있으면 실패 결과로 반환한다")
    void fetchPriceCandles_emptyDailyResponse_returnsFailedResult() {
        when(kisApiClient.fetchDailyItemChartPrice(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(KisDto.DailyItemChartPriceResponse.builder()
                        .rt_cd("0")
                        .output2(List.of())
                        .build());

        CandleFetchResult result = client.fetchPriceCandles(STOCK_ID, START, END, Timeframe.DAY);

        assertThat(result.status()).isEqualTo(CandleFetchResult.Status.FAILED);
    }

    @Test
    @DisplayName("일봉 페이지 일부 확보 후 후속 호출이 실패하면 부분 성공으로 반환한다")
    void fetchPriceCandles_laterDailyPageFailure_returnsPartialResult() {
        KisDto.DailyItemChartPriceOutput2 item = dailyItem("20260820");
        KisDto.DailyItemChartPriceResponse firstPage = KisDto.DailyItemChartPriceResponse.builder()
                .rt_cd("0")
                .output2(Collections.nCopies(100, item))
                .build();
        when(kisApiClient.fetchDailyItemChartPrice(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(firstPage)
                .thenThrow(new DomainException(GlobalErrorCode.CIRCUIT_BREAKER_OPEN));

        CandleFetchResult result = client.fetchPriceCandles(STOCK_ID, START, END, Timeframe.DAY);

        assertThat(result.status()).isEqualTo(CandleFetchResult.Status.PARTIAL);
        assertThat(result.candles()).hasSize(1);
    }

    @Test
    @DisplayName("정상 일봉 응답은 완료 결과로 반환한다")
    void fetchPriceCandles_dailySuccess_returnsCompleteResult() {
        when(kisApiClient.fetchDailyItemChartPrice(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(KisDto.DailyItemChartPriceResponse.builder()
                        .rt_cd("0")
                        .output2(List.of(dailyItem("20260820")))
                        .build());

        CandleFetchResult result = client.fetchPriceCandles(STOCK_ID, START, END, Timeframe.DAY);

        assertThat(result.status()).isEqualTo(CandleFetchResult.Status.COMPLETE);
        assertThat(result.candles()).hasSize(1);
    }

    @Test
    @DisplayName("정상 주봉 응답은 주 시작일로 정규화해 반환한다")
    void fetchPriceCandles_weeklySuccess_normalizesToWeekStart() {
        LocalDateTime weekStart = LocalDateTime.of(2026, 8, 17, 0, 0);
        when(kisApiClient.fetchDailyItemChartPrice(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(KisDto.DailyItemChartPriceResponse.builder()
                        .rt_cd("0")
                        .output2(List.of(dailyItem("20260820")))
                        .build());

        CandleFetchResult result = client.fetchPriceCandles(
                STOCK_ID, weekStart, weekStart, Timeframe.WEEK);

        assertThat(result.status()).isEqualTo(CandleFetchResult.Status.COMPLETE);
        assertThat(result.candles()).extracting(candle -> candle.getAt()).containsExactly(weekStart);
    }

    private KisDto.DailyItemChartPriceOutput2 dailyItem(String date) {
        return KisDto.DailyItemChartPriceOutput2.builder()
                .stck_bsop_date(date)
                .stck_oprc("70000")
                .stck_clpr("70500")
                .stck_hgpr("71000")
                .stck_lwpr("69000")
                .acml_vol("1000")
                .acml_tr_pbmn("70500000")
                .prdy_vrss_sign("2")
                .prdy_vrss("500")
                .build();
    }
}
