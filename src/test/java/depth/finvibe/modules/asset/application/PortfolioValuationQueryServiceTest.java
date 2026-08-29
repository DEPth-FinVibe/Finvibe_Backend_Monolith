package depth.finvibe.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.application.port.out.ValuationCacheRepository;
import depth.finvibe.modules.asset.application.port.out.ValuationSnapshotRepository;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioSnapshot;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.UserSnapshot;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationQueryServiceTest {
    private static final Long USER_ID = 1L;
    private static final String CACHE_USER_ID = "1";
    private static final Instant UPDATED_AT = Instant.parse("2026-08-29T10:00:00Z");

    @Mock
    private PortfolioGroupRepository portfolioGroupRepository;
    @Mock
    private ValuationCacheRepository valuationCacheRepository;
    @Mock
    private ValuationSnapshotRepository valuationSnapshotRepository;

    private PortfolioValuationQueryService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioValuationQueryService(
            portfolioGroupRepository,
            valuationCacheRepository,
            valuationSnapshotRepository,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void returnsRedisValuesWithoutDatabaseFallback() {
        mockPortfolios(10L);
        UserSnapshot user = userSnapshot();
        PortfolioSnapshot portfolio = portfolioSnapshot(10L, 600L, 630L);
        when(valuationCacheRepository.findUser(CACHE_USER_ID)).thenReturn(Optional.of(user));
        when(valuationCacheRepository.findPortfolios(List.of(10L))).thenReturn(Map.of(10L, portfolio));

        var response = service.getValuations(USER_ID);

        assertThat(response.user().currentValue()).isEqualTo(1050L);
        assertThat(response.portfolios()).containsExactly(
            depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioValuationResponse.from(portfolio)
        );
        verify(valuationSnapshotRepository, never()).findUser(CACHE_USER_ID);
        verify(valuationSnapshotRepository, never()).findPortfolios(List.of(10L));
    }

    @Test
    void fallsBackOnlyMissingPortfolioAndRefillsIt() {
        mockPortfolios(10L, 20L);
        PortfolioSnapshot cached = portfolioSnapshot(10L, 600L, 630L);
        PortfolioSnapshot database = portfolioSnapshot(20L, 400L, 420L);
        when(valuationCacheRepository.findUser(CACHE_USER_ID)).thenReturn(Optional.of(userSnapshot()));
        when(valuationCacheRepository.findPortfolios(List.of(10L, 20L))).thenReturn(Map.of(10L, cached));
        when(valuationSnapshotRepository.findPortfolios(List.of(20L))).thenReturn(Map.of(20L, database));

        var response = service.getValuations(USER_ID);

        assertThat(response.portfolios()).extracting(it -> it.portfolioId()).containsExactly(10L, 20L);
        assertThat(response.portfolios()).extracting(it -> it.currentValue()).containsExactly(630L, 420L);
        verify(valuationCacheRepository).cachePortfolioIfNewer(database);
    }

    @Test
    void returnsDatabaseValuesWhenRedisConnectionFails() {
        mockPortfolios(10L);
        UserSnapshot user = userSnapshot();
        PortfolioSnapshot portfolio = portfolioSnapshot(10L, 600L, 630L);
        when(valuationCacheRepository.findUser(CACHE_USER_ID)).thenThrow(new IllegalStateException("redis down"));
        when(valuationCacheRepository.findPortfolios(List.of(10L))).thenThrow(new IllegalStateException("redis down"));
        when(valuationSnapshotRepository.findUser(CACHE_USER_ID)).thenReturn(Optional.of(user));
        when(valuationSnapshotRepository.findPortfolios(List.of(10L))).thenReturn(Map.of(10L, portfolio));

        var response = service.getValuations(USER_ID);

        assertThat(response.user().updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(response.portfolios()).extracting(it -> it.currentValue()).containsExactly(630L);
        verify(valuationCacheRepository).cacheUserIfNewer(user);
        verify(valuationCacheRepository).cachePortfolioIfNewer(portfolio);
    }

    @Test
    void returnsZeroValuesWhenBothSourcesMiss() {
        mockPortfolios(10L);
        when(valuationCacheRepository.findUser(CACHE_USER_ID)).thenReturn(Optional.empty());
        when(valuationCacheRepository.findPortfolios(List.of(10L))).thenReturn(Map.of());
        when(valuationSnapshotRepository.findUser(CACHE_USER_ID)).thenReturn(Optional.empty());
        when(valuationSnapshotRepository.findPortfolios(List.of(10L))).thenReturn(Map.of());

        var response = service.getValuations(USER_ID);

        assertThat(response.user().purchasedValue()).isZero();
        assertThat(response.user().portfolioCount()).isEqualTo(1L);
        assertThat(response.user().updatedAt()).isNull();
        assertThat(response.portfolios()).singleElement().satisfies(portfolio -> {
            assertThat(portfolio.portfolioId()).isEqualTo(10L);
            assertThat(portfolio.currentValue()).isZero();
            assertThat(portfolio.updatedAt()).isNull();
        });
    }

    private void mockPortfolios(Long... ids) {
        List<PortfolioGroup> portfolios = java.util.Arrays.stream(ids)
            .map(id -> {
                PortfolioGroup portfolio = mock(PortfolioGroup.class);
                when(portfolio.getId()).thenReturn(id);
                return portfolio;
            })
            .toList();
        when(portfolioGroupRepository.findAllByUserId(USER_ID)).thenReturn(portfolios);
    }

    private UserSnapshot userSnapshot() {
        return new UserSnapshot(CACHE_USER_ID, 1000L, 1050L, 5.0, 2L, UPDATED_AT);
    }

    private PortfolioSnapshot portfolioSnapshot(Long id, long purchasedValue, long currentValue) {
        return new PortfolioSnapshot(id, purchasedValue, currentValue, 5.0, 3L, UPDATED_AT);
    }
}
