package depth.finvibe.modules.asset.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.infra.redis.PortfolioAssetSnapshotRedisRepository;
import depth.finvibe.modules.asset.infra.redis.PortfolioAssetSnapshotRedisRepository.AssetSnapshot;
import depth.finvibe.modules.asset.infra.redis.PortfolioOwnerRedisRepository;
import depth.finvibe.modules.asset.infra.redis.StockHoldingIndexRedisRepository;

/**
 * 가격 이벤트 도착 시 해당 종목의 Redis 인덱스가 없으면 DB에서 lazy load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIndexWarmupService {

	private final PortfolioGroupRepository portfolioGroupRepository;
	private final StockHoldingIndexRedisRepository stockHoldingIndexRedisRepository;
	private final PortfolioAssetSnapshotRedisRepository portfolioAssetSnapshotRedisRepository;
	private final PortfolioOwnerRedisRepository portfolioOwnerRedisRepository;

	/**
	 * 특정 종목의 Redis 인덱스가 없으면 DB에서 조회하여 Redis에 적재한다.
	 */
	@Transactional(readOnly = true)
	public void warmupIfAbsent(Long stockId) {
		if (stockHoldingIndexRedisRepository.exists(stockId)) {
			return;
		}

		List<PortfolioGroup> portfolios = portfolioGroupRepository.findAllByStockIdsWithAssets(List.of(stockId));
		if (portfolios.isEmpty()) {
			return;
		}

		Set<Long> portfolioIds = portfolios.stream()
				.map(PortfolioGroup::getId)
				.collect(Collectors.toSet());

		stockHoldingIndexRedisRepository.replacePortfolios(stockId, portfolioIds);

		for (PortfolioGroup portfolio : portfolios) {
			portfolioOwnerRedisRepository.set(portfolio.getId(), portfolio.getUserId());

			for (Asset asset : portfolio.getAssets()) {
				if (asset.getStockId().equals(stockId)) {
					portfolioAssetSnapshotRedisRepository.putAsset(
							portfolio.getId(),
							asset.getStockId(),
							asset.getAmount(),
							asset.getTotalPrice().getAmount(),
							asset.getTotalPrice().getCurrency().name()
					);
				}
			}
		}

		log.info("Warmed up Redis index for stockId={}: {} portfolios loaded", stockId, portfolios.size());
	}

	/**
	 * 전체 데이터를 청킹하여 Redis에 적재한다. 서비스 기동 후 백그라운드에서 호출.
	 */
	@Transactional(readOnly = true)
	public void warmupAll() {
		List<PortfolioGroup> allPortfolios = portfolioGroupRepository.findAllWithAssets();

		Map<Long, Set<Long>> stockToPortfolios = allPortfolios.stream()
				.flatMap(pg -> pg.getAssets().stream()
						.map(asset -> Map.entry(asset.getStockId(), pg.getId())))
				.collect(Collectors.groupingBy(
						Map.Entry::getKey,
						Collectors.mapping(Map.Entry::getValue, Collectors.toSet())
				));

		for (Map.Entry<Long, Set<Long>> entry : stockToPortfolios.entrySet()) {
			stockHoldingIndexRedisRepository.replacePortfolios(entry.getKey(), entry.getValue());
		}

		for (PortfolioGroup portfolio : allPortfolios) {
			portfolioOwnerRedisRepository.set(portfolio.getId(), portfolio.getUserId());

			Map<Long, AssetSnapshot> snapshots = portfolio.getAssets().stream()
					.collect(Collectors.toMap(
							Asset::getStockId,
							asset -> new AssetSnapshot(
									asset.getAmount(),
									asset.getTotalPrice().getAmount(),
									asset.getTotalPrice().getCurrency().name()
							),
							(a, b) -> a
					));

			if (!snapshots.isEmpty()) {
				portfolioAssetSnapshotRedisRepository.replaceAll(portfolio.getId(), snapshots);
			}
		}

		log.info("Full warm-up completed: {} portfolios, {} stocks indexed",
				allPortfolios.size(), stockToPortfolios.size());
	}
}
