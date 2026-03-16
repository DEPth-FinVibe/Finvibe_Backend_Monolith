package depth.finvibe.modules.asset.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

public interface PortfolioGroupRepository {
    PortfolioGroup save(PortfolioGroup portfolioGroup);

    List<PortfolioGroup> saveAll(List<PortfolioGroup> portfolioGroups);

    Optional<PortfolioGroup> findById(Long id);

    Optional<PortfolioGroup> findByIdWithAssets(Long id);

    List<PortfolioGroup> findAllByUserId(UUID userId);

    List<PortfolioGroup> findAllByUserIdWithAssets(UUID userId);

    List<PortfolioGroup> findAllWithAssets();

    Optional<PortfolioGroup> findDefaultByUserId(UUID userId);

    List<PortfolioGroup> findAllByStockIdsWithAssets(List<Long> stockIds);

    List<TopHoldingStockDto.TopHoldingStockResponse> findTopHoldingStocks(int limit);

    void delete(PortfolioGroup existing);

    boolean existDefaultByUserId(UUID userId);

    boolean existsByIdAndUserId(Long portfolioId, UUID userId);
}
