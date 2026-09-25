package depth.finvibe.modules.asset.infra.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.ValuationSnapshotRepository;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.PortfolioSnapshot;
import depth.finvibe.modules.asset.dto.PortfolioValuationDto.UserSnapshot;

@Repository
@RequiredArgsConstructor
public class ValuationSnapshotRepositoryImpl implements ValuationSnapshotRepository {
    private final PortfolioValuationReadJpaRepository portfolioRepository;
    private final UserValuationReadJpaRepository userRepository;

    @Override
    public Optional<UserSnapshot> findUser(String userId) {
        return userRepository.findById(userId).map(this::toSnapshot);
    }

    @Override
    public Map<Long, PortfolioSnapshot> findPortfolios(List<Long> portfolioIds) {
        if (portfolioIds.isEmpty()) {
            return Map.of();
        }
        return portfolioRepository.findActiveByPortfolioIds(portfolioIds).stream()
            .map(this::toSnapshot)
            .collect(Collectors.toMap(PortfolioSnapshot::portfolioId, Function.identity()));
    }

    private UserSnapshot toSnapshot(UserValuationReadEntity entity) {
        return new UserSnapshot(
            entity.getUserId(),
            entity.getPurchasedValue(),
            entity.getCurrentValue(),
            entity.getProfitRate(),
            entity.getPortfolioCount(),
            entity.getUpdatedAt()
        );
    }

    private PortfolioSnapshot toSnapshot(PortfolioValuationReadEntity entity) {
        return new PortfolioSnapshot(
            entity.getPortfolioId(),
            entity.getPurchasedValue(),
            entity.getCurrentValue(),
            entity.getProfitRate(),
            entity.getAssetCount(),
            entity.getUpdatedAt()
        );
    }
}
