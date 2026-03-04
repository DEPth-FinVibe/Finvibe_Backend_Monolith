package depth.finvibe.modules.asset.infra.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.UserProfitRankingBadgeService;

@Component
@RequiredArgsConstructor
public class UserProfitRankingScheduler {
  private final UserProfitRankingBadgeService userProfitRankingBadgeService;

  @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
  @SchedulerLock(
    name = "userProfitRankingWeeklyBadge",
    lockAtMostFor = "PT10M",
    lockAtLeastFor = "PT10S"
  )
  public void rewardWeeklyTopOnePercentBadge() {
    userProfitRankingBadgeService.rewardWeeklyTopOnePercentBadge();
  }

  @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
  @SchedulerLock(
    name = "userProfitRankingMonthlyBadge",
    lockAtMostFor = "PT10M",
    lockAtLeastFor = "PT10S"
  )
  public void rewardMonthlyTopOnePercentBadge() {
    userProfitRankingBadgeService.rewardMonthlyTopOnePercentBadge();
  }
}
