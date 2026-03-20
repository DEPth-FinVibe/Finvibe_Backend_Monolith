package depth.finvibe.modules.asset.infra.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.UserProfitRankingBadgeService;

@Component
@RequiredArgsConstructor
public class UserProfitRankingScheduler {
  private final UserProfitRankingBadgeService userProfitRankingBadgeService;

  public void rewardWeeklyTopOnePercentBadge() {
    userProfitRankingBadgeService.rewardWeeklyTopOnePercentBadge();
  }

  public void rewardMonthlyTopOnePercentBadge() {
    userProfitRankingBadgeService.rewardMonthlyTopOnePercentBadge();
  }
}
