package depth.finvibe.modules.asset.application.port.in;

import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.asset.dto.PortfolioGroupDto;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

public interface AssetQueryUseCase {
    List<PortfolioGroupDto.AssetResponse> getAssetsByPortfolio(Long portfolioId, UUID requesterUserId);
    List<PortfolioGroupDto.PortfolioGroupResponse> getPortfoliosByUser(UUID userId);
    boolean isExistPortfolio(Long portfolioId, UUID userId);
    boolean hasSufficientStockAmount(Long portfolioId, UUID userId, Long stockId, Double amount);
    TopHoldingStockDto.TopHoldingStockListResponse getTopHoldingStocks(UUID userId);
    PortfolioGroupDto.AssetAllocationResponse getAssetAllocation(UUID requesterUserId);
}
