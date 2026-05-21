package depth.finvibe.modules.asset.application.port.in;

import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.asset.dto.PortfolioGroupDto;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

public interface AssetQueryUseCase {
    List<PortfolioGroupDto.AssetResponse> getAssetsByPortfolio(Long portfolioId, Long requesterUserId);
    List<PortfolioGroupDto.PortfolioGroupResponse> getPortfoliosByUser(Long userId);
    boolean isExistPortfolio(Long portfolioId, Long userId);
    boolean hasSufficientStockAmount(Long portfolioId, Long userId, Long stockId, Double amount);
    TopHoldingStockDto.TopHoldingStockListResponse getTopHoldingStocks(Long userId);
    PortfolioGroupDto.AssetAllocationResponse getAssetAllocation(Long requesterUserId);
}
