package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.temporal.TemporalAdjusters;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.out.UserProfitRankingData;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingRepository;
import depth.finvibe.modules.asset.application.port.out.UserProfitSnapshotRepository;
import depth.finvibe.modules.asset.domain.UserProfitSnapshotDaily;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

@Service
@RequiredArgsConstructor
public class UserProfitRankingAggregationService {
  private final UserProfitSnapshotRepository userProfitSnapshotRepository;
  private final UserProfitRankingRepository userProfitRankingRepository;

  @Transactional
  public void aggregateRollingRankings(
    LocalDate today,
    Map<UUID, CurrentUserProfitData> currentProfitDataByUser
  ) {
    if (today == null || currentProfitDataByUser == null || currentProfitDataByUser.isEmpty()) {
      userProfitRankingRepository.replaceAllRankings(UserProfitRankType.WEEKLY, List.of());
      userProfitRankingRepository.replaceAllRankings(UserProfitRankType.MONTHLY, List.of());
      return;
    }

    LocalDate weeklyStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate monthlyStart = today.withDayOfMonth(1);

    aggregateRankings(
      UserProfitRankType.WEEKLY,
      weeklyStart.minusDays(1),
      currentProfitDataByUser
    );
    aggregateRankings(
      UserProfitRankType.MONTHLY,
      monthlyStart.minusDays(1),
      currentProfitDataByUser
    );
  }

  private void aggregateRankings(
    UserProfitRankType rankType,
    LocalDate startSnapshotDate,
    Map<UUID, CurrentUserProfitData> currentProfitDataByUser
  ) {
    List<UserProfitSnapshotDaily> startSnapshots = userProfitSnapshotRepository.findBySnapshotDate(startSnapshotDate);

    if (startSnapshots.isEmpty()) {
      userProfitRankingRepository.replaceAllRankings(rankType, List.of());
      return;
    }

    Map<UUID, UserProfitSnapshotDaily> startByUser = toUserMap(startSnapshots);

    List<UserProfitRankingData> rankings = new ArrayList<>();
    Collection<UUID> targetUserIds = currentProfitDataByUser.keySet().stream()
      .filter(startByUser::containsKey)
      .collect(Collectors.toSet());
    for (UUID userId : targetUserIds) {
      UserProfitSnapshotDaily startSnapshot = startByUser.get(userId);
      if (startSnapshot == null) {
        continue;
      }
      CurrentUserProfitData currentUserProfitData = currentProfitDataByUser.get(userId);
      if (currentUserProfitData == null) {
        continue;
      }

      BigDecimal periodProfitLoss = currentUserProfitData.totalProfitLoss()
        .subtract(startSnapshot.getTotalProfitLoss());
      BigDecimal periodPurchase = currentUserProfitData.totalCurrentValue()
        .subtract(currentUserProfitData.totalProfitLoss());
      BigDecimal periodReturnRate = calculateReturnRate(periodProfitLoss, periodPurchase);

      rankings.add(new UserProfitRankingData(
        userId,
        currentUserProfitData.userNickname(),
        periodReturnRate,
        periodProfitLoss
      ));
    }

    userProfitRankingRepository.replaceAllRankings(rankType, rankings);
  }

  private Map<UUID, UserProfitSnapshotDaily> toUserMap(List<UserProfitSnapshotDaily> snapshots) {
    Map<UUID, UserProfitSnapshotDaily> map = new HashMap<>();
    for (UserProfitSnapshotDaily snapshot : snapshots) {
      map.put(snapshot.getId().getUserId(), snapshot);
    }
    return map;
  }

  private BigDecimal calculateReturnRate(BigDecimal profitLoss, BigDecimal purchaseAmount) {
    if (purchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return profitLoss
      .divide(purchaseAmount, 4, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }
}
