package depth.finvibe.modules.asset.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.asset.domain.enums.PortfolioChartInterval;
import depth.finvibe.modules.asset.dto.PortfolioPerformanceDto;
import depth.finvibe.modules.asset.dto.PortfolioGroupDto;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

public interface AssetQueryUseCase {
    List<PortfolioGroupDto.AssetResponse> getAssetsByPortfolio(Long portfolioId, UUID requesterUserId);
    List<PortfolioGroupDto.PortfolioGroupResponse> getPortfoliosByUser(UUID userId);
    List<PortfolioGroupDto.PortfolioComparisonResponse> getPortfolioComparisons(UUID userId);
    boolean isExistPortfolio(Long portfolioId, UUID userId);
    boolean hasSufficientStockAmount(Long portfolioId, UUID userId, Long stockId, Double amount);
    TopHoldingStockDto.TopHoldingStockListResponse getTopHoldingStocks(UUID userId);
    PortfolioGroupDto.AssetAllocationResponse getAssetAllocation(UUID requesterUserId);
    PortfolioPerformanceDto.ChartResponse getPortfolioPerformanceChart(
            UUID requesterUserId,
            LocalDate startDate,
            LocalDate endDate,
            PortfolioChartInterval interval
    );
}
