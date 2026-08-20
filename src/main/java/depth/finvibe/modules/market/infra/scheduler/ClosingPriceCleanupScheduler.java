package depth.finvibe.modules.market.infra.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.ClosingPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClosingPriceCleanupScheduler {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private static final int RETENTION_DAYS = 30;

  private final ClosingPriceRepository closingPriceRepository;

  public void cleanupExpiredClosingPrices() {
    LocalDate cutoffDate = LocalDate.now(KOREA_ZONE).minusDays(RETENTION_DAYS);
    long deletedCount = closingPriceRepository.deleteByTradingDateBefore(cutoffDate);
    log.info("보존 기간이 지난 종가를 정리했습니다. cutoffDate={}, deletedCount={}", cutoffDate, deletedCount);
  }
}
