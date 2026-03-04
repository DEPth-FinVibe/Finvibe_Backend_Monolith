package depth.finvibe.modules.asset.infra.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.PortfolioPerformanceSnapshotService;

@Component
@RequiredArgsConstructor
public class PortfolioPerformanceSnapshotScheduler {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final PortfolioPerformanceSnapshotService portfolioPerformanceSnapshotService;

  @Scheduled(cron = "0 7 0 * * *", zone = "Asia/Seoul")
  @SchedulerLock(
    name = "portfolioPerformanceSnapshotDaily",
    lockAtMostFor = "PT10M",
    lockAtLeastFor = "PT10S"
  )
  public void saveDailySnapshot() {
    LocalDate snapshotDate = LocalDate.now(KST).minusDays(1);
    portfolioPerformanceSnapshotService.saveDailySnapshot(snapshotDate);
  }
}
