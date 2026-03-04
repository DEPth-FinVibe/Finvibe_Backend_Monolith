package depth.finvibe.modules.asset.application;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.out.UserProfitRankingQueryRepository;
import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;
import depth.finvibe.common.investment.application.port.out.GamificationEventProducer;
import depth.finvibe.common.investment.dto.Badge;
import depth.finvibe.common.investment.dto.RewardBadgeEvent;

@Service
@RequiredArgsConstructor
public class UserProfitRankingBadgeService {
  private final UserProfitRankingQueryRepository userProfitRankingQueryRepository;
  private final GamificationEventProducer gamificationEventProducer;

  @Transactional(readOnly = true)
  public void rewardWeeklyTopOnePercentBadge() {
    rewardTopOnePercentBadge(UserProfitRankType.WEEKLY, "주간 수익률 상위 1% 선정");
  }

  @Transactional(readOnly = true)
  public void rewardMonthlyTopOnePercentBadge() {
    rewardTopOnePercentBadge(UserProfitRankType.MONTHLY, "월간 수익률 상위 1% 선정");
  }

  private void rewardTopOnePercentBadge(UserProfitRankType rankType, String reason) {
    Page<UserProfitRanking> rankingCountPage = userProfitRankingQueryRepository.findByRankType(
      rankType,
      PageRequest.of(0, 1)
    );
    long totalCount = rankingCountPage.getTotalElements();
    if (totalCount <= 0) {
      return;
    }

    int topCount = Math.max(1, (int) Math.ceil(totalCount * 0.01d));
    Page<UserProfitRanking> topRankingsPage = userProfitRankingQueryRepository.findByRankType(
      rankType,
      PageRequest.of(0, topCount)
    );

    Instant issuedAt = Instant.now();
    for (UserProfitRanking ranking : topRankingsPage.getContent()) {
      gamificationEventProducer.publishRewardBadgeEvent(RewardBadgeEvent.builder()
        .userId(ranking.getUserId().toString())
        .badgeCode(Badge.TOP_ONE_PERCENT_TRAINER.name())
        .issuedAt(issuedAt)
        .reason(reason)
        .build());
    }
  }
}
