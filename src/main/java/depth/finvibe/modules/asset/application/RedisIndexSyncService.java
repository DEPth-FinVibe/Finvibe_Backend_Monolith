package depth.finvibe.modules.asset.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import depth.finvibe.modules.asset.application.event.AssetRegisteredEvent;
import depth.finvibe.modules.asset.application.event.AssetUnregisteredEvent;
import depth.finvibe.modules.asset.application.event.AssetTransferredEvent;
import depth.finvibe.modules.asset.application.event.PortfolioCreatedEvent;
import depth.finvibe.modules.asset.application.event.PortfolioDeletedEvent;
import depth.finvibe.modules.asset.infra.redis.PortfolioAssetSnapshotRedisRepository;
import depth.finvibe.modules.asset.infra.redis.PortfolioOwnerRedisRepository;
import depth.finvibe.modules.asset.infra.redis.StockHoldingIndexRedisRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIndexSyncService {

	private final StockHoldingIndexRedisRepository stockHoldingIndexRedisRepository;
	private final PortfolioAssetSnapshotRedisRepository portfolioAssetSnapshotRedisRepository;
	private final PortfolioOwnerRedisRepository portfolioOwnerRedisRepository;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleAssetRegistered(AssetRegisteredEvent event) {
		try {
			stockHoldingIndexRedisRepository.addPortfolio(event.getStockId(), event.getPortfolioId());

			portfolioAssetSnapshotRedisRepository.putAsset(
					event.getPortfolioId(),
					event.getStockId(),
					event.getAmount(),
					event.getPurchasePriceAmount(),
					event.getCurrency()
			);

			portfolioOwnerRedisRepository.set(event.getPortfolioId(), event.getUserId());

			log.debug("Redis index synced for asset registered: portfolioId={}, stockId={}",
					event.getPortfolioId(), event.getStockId());
		} catch (Exception e) {
			log.warn("Failed to sync Redis index for asset registered: portfolioId={}, stockId={}",
					event.getPortfolioId(), event.getStockId(), e);
		}
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleAssetUnregistered(AssetUnregisteredEvent event) {
		try {
			if (event.isFullyRemoved()) {
				stockHoldingIndexRedisRepository.removePortfolio(event.getStockId(), event.getPortfolioId());
				portfolioAssetSnapshotRedisRepository.removeAsset(event.getPortfolioId(), event.getStockId());
			} else {
				portfolioAssetSnapshotRedisRepository.putAsset(
						event.getPortfolioId(),
						event.getStockId(),
						event.getRemainingAmount(),
						event.getRemainingPurchasePriceAmount(),
						event.getCurrency()
				);
			}

			log.debug("Redis index synced for asset unregistered: portfolioId={}, stockId={}, fullyRemoved={}",
					event.getPortfolioId(), event.getStockId(), event.isFullyRemoved());
		} catch (Exception e) {
			log.warn("Failed to sync Redis index for asset unregistered: portfolioId={}, stockId={}",
					event.getPortfolioId(), event.getStockId(), e);
		}
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleAssetTransferred(AssetTransferredEvent event) {
		try {
			stockHoldingIndexRedisRepository.removePortfolio(event.getStockId(), event.getSourcePortfolioId());
			portfolioAssetSnapshotRedisRepository.removeAsset(event.getSourcePortfolioId(), event.getStockId());

			stockHoldingIndexRedisRepository.addPortfolio(event.getStockId(), event.getTargetPortfolioId());
			portfolioAssetSnapshotRedisRepository.putAsset(
					event.getTargetPortfolioId(),
					event.getStockId(),
					event.getTargetAmount(),
					event.getTargetPurchasePriceAmount(),
					event.getTargetCurrency()
			);

			log.debug("Redis index synced for asset transferred: source={}, target={}, stockId={}, merged={}",
					event.getSourcePortfolioId(), event.getTargetPortfolioId(), event.getStockId(), event.isMerged());
		} catch (Exception e) {
			log.warn("Failed to sync Redis index for asset transferred: source={}, target={}, stockId={}",
					event.getSourcePortfolioId(), event.getTargetPortfolioId(), event.getStockId(), e);
		}
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handlePortfolioCreated(PortfolioCreatedEvent event) {
		try {
			portfolioOwnerRedisRepository.set(event.getPortfolioId(), event.getUserId());

			log.debug("Redis index synced for portfolio created: portfolioId={}, userId={}",
					event.getPortfolioId(), event.getUserId());
		} catch (Exception e) {
			log.warn("Failed to sync Redis index for portfolio created: portfolioId={}",
					event.getPortfolioId(), e);
		}
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handlePortfolioDeleted(PortfolioDeletedEvent event) {
		try {
			for (Long stockId : event.getStockIds()) {
				stockHoldingIndexRedisRepository.removePortfolio(stockId, event.getDeletedPortfolioId());
				stockHoldingIndexRedisRepository.addPortfolio(stockId, event.getDefaultPortfolioId());
			}

			portfolioAssetSnapshotRedisRepository.deleteAll(event.getDeletedPortfolioId());
			portfolioOwnerRedisRepository.delete(event.getDeletedPortfolioId());

			log.debug("Redis index synced for portfolio deleted: deletedId={}, defaultId={}",
					event.getDeletedPortfolioId(), event.getDefaultPortfolioId());
		} catch (Exception e) {
			log.warn("Failed to sync Redis index for portfolio deleted: deletedId={}",
					event.getDeletedPortfolioId(), e);
		}
	}
}
