package depth.finvibe.modules.asset.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

@Repository
@RequiredArgsConstructor
public class PortfolioGroupRepositoryImpl implements PortfolioGroupRepository {
    private final PortfolioGroupJpaRepository jpaRepository;
    private final PortfolioGroupQueryRepository queryRepository;

    @Override
    public PortfolioGroup save(PortfolioGroup portfolioGroup) {
        return jpaRepository.save(portfolioGroup);
    }

    @Override
    public List<PortfolioGroup> saveAll(List<PortfolioGroup> portfolioGroups) {
        return jpaRepository.saveAll(portfolioGroups);
    }

    @Override
    public Optional<PortfolioGroup> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<PortfolioGroup> findByIdWithAssets(Long id) {
        return queryRepository.findByIdWithAssets(id);
    }

    @Override
    public List<PortfolioGroup> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<PortfolioGroup> findAllByUserIdWithAssets(UUID userId) {
        return queryRepository.findAllByUserIdWithAssets(userId);
    }

    @Override
    public List<PortfolioGroup> findAllWithAssets() {
        return queryRepository.findAllWithAssets();
    }

    @Override
    public Optional<PortfolioGroup> findDefaultByUserId(UUID userId) {
        return queryRepository.findDefaultByUserId(userId);
    }

    @Override
    public List<PortfolioGroup> findAllByStockIdsWithAssets(List<Long> stockIds) {
        return queryRepository.findAllByStockIdsWithAssets(stockIds);
    }

    @Override
    public List<TopHoldingStockDto.TopHoldingStockResponse> findTopHoldingStocks(int limit) {
        return queryRepository.findTopHoldingStocks(limit);
    }

    @Override
    public boolean existDefaultByUserId(UUID userId) {
        return queryRepository.existDefaultByUserId(userId);
    }

    @Override
    public boolean existsByIdAndUserId(Long portfolioId, UUID userId) {
        return jpaRepository.existsByIdAndUserId(portfolioId, userId);
    }

    @Override
    public void delete(PortfolioGroup existing) {
        jpaRepository.delete(existing);
    }
}
