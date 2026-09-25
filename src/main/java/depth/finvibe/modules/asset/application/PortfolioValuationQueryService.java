package depth.finvibe.modules.asset.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.in.PortfolioValuationQueryUseCase;
import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.application.port.out.ValuationCacheRepository;
import depth.finvibe.modules.asset.application.port.out.ValuationSnapshotRepository;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioSnapshot;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.UserSnapshot;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioValuationQueryService implements PortfolioValuationQueryUseCase {
    private final PortfolioGroupRepository portfolioGroupRepository;
    private final ValuationCacheRepository valuationCacheRepository;
    private final ValuationSnapshotRepository valuationSnapshotRepository;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public PortfolioValuationDto.ValuationsResponse getValuations(Long userId) {
        String cacheUserId = userId.toString();
        List<Long> portfolioIds = portfolioGroupRepository.findAllByUserId(userId).stream()
            .map(PortfolioGroup::getId)
            .toList();

        Optional<UserSnapshot> cachedUser = findCachedUser(cacheUserId);
        Optional<UserSnapshot> user = cachedUser.isPresent()
            ? cachedUser
            : valuationSnapshotRepository.findUser(cacheUserId);
        String userSource = cachedUser.isPresent() ? "redis" : user.isPresent() ? "database" : "zero";
        recordSource("user", userSource);
        if (cachedUser.isEmpty()) {
            user.ifPresent(snapshot -> refill(snapshot, valuationCacheRepository::cacheUserIfNewer));
        }

        Map<Long, PortfolioSnapshot> cachedPortfolios = findCachedPortfolios(portfolioIds);
        List<Long> missingIds = portfolioIds.stream()
            .filter(portfolioId -> !cachedPortfolios.containsKey(portfolioId))
            .toList();
        Map<Long, PortfolioSnapshot> databasePortfolios = missingIds.isEmpty()
            ? Map.of()
            : valuationSnapshotRepository.findPortfolios(missingIds);
        databasePortfolios.values().forEach(snapshot ->
            refill(snapshot, valuationCacheRepository::cachePortfolioIfNewer));

        Map<Long, PortfolioSnapshot> merged = new LinkedHashMap<>(cachedPortfolios);
        merged.putAll(databasePortfolios);
        List<PortfolioValuationDto.PortfolioValuationResponse> portfolios = portfolioIds.stream()
            .map(portfolioId -> toResponse(portfolioId, cachedPortfolios, databasePortfolios, merged))
            .toList();

        PortfolioValuationDto.UserValuationResponse userResponse = user
            .map(snapshot -> PortfolioValuationDto.UserValuationResponse.from(userId, snapshot))
            .orElseGet(() -> PortfolioValuationDto.UserValuationResponse.zero(userId, portfolioIds.size()));
        return new PortfolioValuationDto.ValuationsResponse(userResponse, portfolios);
    }

    private Optional<UserSnapshot> findCachedUser(String userId) {
        try {
            return valuationCacheRepository.findUser(userId);
        } catch (RuntimeException ex) {
            recordCacheFailure("user");
            log.warn("Failed to read user valuation cache. userId={}", userId, ex);
            return Optional.empty();
        }
    }

    private Map<Long, PortfolioSnapshot> findCachedPortfolios(List<Long> portfolioIds) {
        if (portfolioIds.isEmpty()) {
            return Map.of();
        }
        try {
            return valuationCacheRepository.findPortfolios(portfolioIds);
        } catch (RuntimeException ex) {
            recordCacheFailure("portfolio");
            log.warn("Failed to read portfolio valuation cache. count={}", portfolioIds.size(), ex);
            return Map.of();
        }
    }

    private PortfolioValuationDto.PortfolioValuationResponse toResponse(
        Long portfolioId,
        Map<Long, PortfolioSnapshot> cached,
        Map<Long, PortfolioSnapshot> database,
        Map<Long, PortfolioSnapshot> merged
    ) {
        String source = cached.containsKey(portfolioId)
            ? "redis"
            : database.containsKey(portfolioId) ? "database" : "zero";
        recordSource("portfolio", source);
        PortfolioSnapshot snapshot = merged.get(portfolioId);
        return snapshot == null
            ? PortfolioValuationDto.PortfolioValuationResponse.zero(portfolioId)
            : PortfolioValuationDto.PortfolioValuationResponse.from(snapshot);
    }

    private <T> void refill(T snapshot, Consumer<T> cacheWriter) {
        try {
            cacheWriter.accept(snapshot);
        } catch (RuntimeException ex) {
            recordCacheFailure("refill");
            log.debug("Skip valuation cache refill", ex);
        }
    }

    private void recordSource(String type, String source) {
        meterRegistry.counter("asset.valuation.read", "type", type, "source", source).increment();
    }

    private void recordCacheFailure(String operation) {
        meterRegistry.counter("asset.valuation.cache.failure", "operation", operation).increment();
    }
}
