package depth.finvibe.modules.market.infra.scheduler;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.StaleCurrentPriceRecoveryService;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaleCurrentPriceRecoveryScheduler {

  private final StaleCurrentPriceRecoveryService staleCurrentPriceRecoveryService;

  @Value("${market.current-price.stale-recovery.threshold-seconds:3}")
  private long staleThresholdSeconds;

  public void recoverStaleCurrentPrices() {
    if (MarketHours.getCurrentStatus() != MarketStatus.OPEN) {
      return;
    }

    try {
      staleCurrentPriceRecoveryService.recoverStaleCurrentPrices(Duration.ofSeconds(staleThresholdSeconds));
    } catch (Exception ex) {
      log.error("Failed to recover stale current prices", ex);
      throw ex;
    }
  }
}
