package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.UserProfitSummaryRow;
import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.PortfolioGroup;

@Service
@RequiredArgsConstructor
class ProfitCalculationTxHelper {

	private final PortfolioGroupRepository portfolioGroupRepository;

	@Transactional(readOnly = true)
	public List<PortfolioGroup> readPortfoliosByStockIds(List<Long> stockIds) {
		return portfolioGroupRepository.findAllByStockIdsWithAssets(stockIds);
	}

	@Transactional
	public void updateValuations(List<PortfolioGroup> portfolios, Map<Long, BigDecimal> priceByStockId) {
		for (PortfolioGroup portfolio : portfolios) {
			for (Asset asset : portfolio.getAssets()) {
				BigDecimal currentPrice = priceByStockId.get(asset.getStockId());
				if (currentPrice != null) {
					asset.updateValuation(currentPrice);
				}
			}
			portfolio.recalculateValuation();
		}
		portfolioGroupRepository.saveAll(portfolios);
	}

	@Transactional(readOnly = true)
	public List<UserProfitSummaryRow> readAllUserProfitSummaries() {
		return portfolioGroupRepository.findAllUserProfitSummaries();
	}

	@Transactional(readOnly = true)
	public List<UUID> readUserIdsWithAssets() {
		return portfolioGroupRepository.findUserIdsWithAssets();
	}
}
