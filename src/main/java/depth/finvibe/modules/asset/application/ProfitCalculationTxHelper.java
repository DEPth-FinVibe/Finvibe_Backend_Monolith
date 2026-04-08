package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
	private static final int DEFAULT_VALUATION_CHUNK_SIZE = 500;

	private final PortfolioGroupRepository portfolioGroupRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional(readOnly = true)
	public List<Long> readAffectedPortfolioIdsByStockIdsAfterId(List<Long> stockIds, Long lastPortfolioId, int limit) {
		return portfolioGroupRepository.findAffectedPortfolioIdsByStockIdsAfterId(stockIds, lastPortfolioId, limit);
	}

	@Transactional(readOnly = true)
	public List<PortfolioGroup> readPortfoliosByIdsWithAssets(List<Long> portfolioIds) {
		return portfolioGroupRepository.findAllByIdsWithAssets(portfolioIds);
	}

	@Transactional
	public void updateValuations(List<PortfolioGroup> portfolios, Map<Long, BigDecimal> priceByStockId, int chunkSize) {
		if (portfolios == null || portfolios.isEmpty()) {
			return;
		}

		int effectiveChunkSize = chunkSize <= 0 ? DEFAULT_VALUATION_CHUNK_SIZE : chunkSize;
		List<PortfolioGroup> dirtyPortfolios = new java.util.ArrayList<>(effectiveChunkSize);

		for (PortfolioGroup portfolio : portfolios) {
			for (Asset asset : portfolio.getAssets()) {
				BigDecimal currentPrice = priceByStockId.get(asset.getStockId());
				if (currentPrice != null) {
					asset.updateValuation(currentPrice);
				}
			}
			portfolio.recalculateValuation();
			dirtyPortfolios.add(portfolio);

			if (dirtyPortfolios.size() >= effectiveChunkSize) {
				portfolioGroupRepository.saveAll(dirtyPortfolios);
				entityManager.flush();
				entityManager.clear();
				dirtyPortfolios.clear();
			}
		}

		if (!dirtyPortfolios.isEmpty()) {
			portfolioGroupRepository.saveAll(dirtyPortfolios);
			entityManager.flush();
			entityManager.clear();
		}
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
