package depth.finvibe.modules.market.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;

import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;
import depth.finvibe.modules.market.infra.client.dto.KisDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealMarketClientImplCurrentPriceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private KisApiClient kisApiClient;
    @Mock
    private StockRepository stockRepository;

    private RealMarketClientImpl client;
    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        // 운영 컨테이너(eclipse-temurin)의 기본 시간대와 같게 둔다.
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        client = new RealMarketClientImpl(kisApiClient, List.of(), stockRepository, new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @DisplayName("현재가 일괄 조회 시각은 JVM 시간대와 무관하게 KIS 실시간 틱과 같은 KST wall clock이다")
    void bulkFetchCurrentPrices_utcJvm_usesKstWallClock() {
        // given
        when(stockRepository.findAllBySymbolIn(List.of("005930"))).thenReturn(List.of(
                Stock.builder().id(4971L).symbol("005930").name("삼성전자").build()
        ));
        when(kisApiClient.fetchIntstockMultpriceBatch(anyList())).thenReturn(List.of(
                KisDto.IntstockMultpriceResponseItem.builder()
                        .inter_shrn_iscd("005930")
                        .inter2_prpr("71200")
                        .build()
        ));
        LocalDateTime before = LocalDateTime.now(KST).truncatedTo(ChronoUnit.MINUTES);

        // when
        LocalDateTime at = client.bulkFetchCurrentPrices(List.of("005930")).getFirst().getAt();

        // then
        assertThat(at).isBetween(before, LocalDateTime.now(KST));
    }
}
