package depth.finvibe.modules.asset.application;

import java.util.LinkedHashSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.in.HoldingStockProjectionUseCase;
import depth.finvibe.modules.asset.application.port.out.AssetRepository;
import depth.finvibe.modules.asset.application.port.out.HoldingStockProjectionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingStockProjectionRebuildService implements HoldingStockProjectionUseCase {

	private final AssetRepository assetRepository;
	private final HoldingStockProjectionRepository holdingStockProjectionRepository;

	// 지금은 asset에서 캐시를 직접 빌드하고 있는데, 너무 비용이 크면 추후 batch작업으로 옮기고 holding stock 자체도 rdbms에서 SoT로 관리하도록 수정
	@Override
	@Transactional(readOnly = true)
	public void rebuildIfEmpty() {
		if (!holdingStockProjectionRepository.isEmpty()) {
			return;
		}

		List<Long> stockIds = assetRepository.findDistinctHoldingStockIds();
		if (stockIds.isEmpty()) {
			log.debug("Redis holding stock projection is empty and no source assets exist.");
			return;
		}

		holdingStockProjectionRepository.replaceHoldingStockIds(new LinkedHashSet<>(stockIds));
		log.info("Empty Redis holding stock projection recovered - stock count: {}", stockIds.size());
	}

	@Override
	@Transactional(readOnly = true)
	public void rebuild() {
		List<Long> stockIds = assetRepository.findDistinctHoldingStockIds();
		holdingStockProjectionRepository.replaceHoldingStockIds(new LinkedHashSet<>(stockIds));
		log.info("Redis holding stock projection rebuilt - stock count: {}", stockIds.size());
	}
}
