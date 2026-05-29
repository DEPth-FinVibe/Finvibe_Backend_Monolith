package depth.finvibe.modules.asset.infra.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.port.in.HoldingStockProjectionUseCase;

@Component
@RequiredArgsConstructor
public class HoldingStockProjectionScheduler {

	private final HoldingStockProjectionUseCase holdingStockProjectionUseCase;

	@EventListener(ApplicationReadyEvent.class)
	public void rebuildOnStartup() {
		holdingStockProjectionUseCase.rebuild();
	}

	@Scheduled(fixedDelayString = "${market.holding.projection.rebuild-empty-interval-ms:60000}")
	public void rebuildIfEmpty() {
		holdingStockProjectionUseCase.rebuildIfEmpty();
	}
}
