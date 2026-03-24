package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.asset.application.event.AllUserProfitRatesUpdatedEvent;
import depth.finvibe.modules.asset.application.port.in.ProfitCalculationUseCase;
import depth.finvibe.modules.asset.application.port.out.MarketPriceClient;
import depth.finvibe.modules.asset.application.port.out.UserNicknameClient;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingData;
import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.common.investment.application.port.out.GamificationEventProducer;
import depth.finvibe.common.investment.dto.BatchPriceSnapshot;
import depth.finvibe.common.investment.dto.MetricEventType;
import depth.finvibe.common.investment.dto.UserMetricUpdatedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitCalculationService implements ProfitCalculationUseCase {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ProfitCalculationTxHelper txHelper;
  private final MarketPriceClient marketPriceClient;
  private final UserNicknameClient userNicknameClient;
  private final UserProfitRankingAggregationService userProfitRankingAggregationService;
  private final ApplicationEventPublisher eventPublisher;
  private final GamificationEventProducer gamificationEventProducer;
  private final MeterRegistry meterRegistry;

  @Override
  public void recalculateAllProfits(List<Long> updatedStockIds) {
    Timer.Sample sample = Timer.start(meterRegistry);
    if (updatedStockIds == null || updatedStockIds.isEmpty()) {
      sample.stop(profitRecalculationTimer());
      return;
    }

    // ① 짧은 read 트랜잭션 — 완료 후 커넥션 즉시 반환
    List<PortfolioGroup> portfolios = txHelper.readPortfoliosByStockIds(updatedStockIds);
    DistributionSummary.builder("asset.profit.recalculation.portfolios")
      .description("수익률 재계산 대상 포트폴리오 수")
      .register(meterRegistry)
      .record(portfolios.size());
    DistributionSummary.builder("asset.profit.recalculation.stocks")
      .description("수익률 재계산 요청 종목 수")
      .register(meterRegistry)
      .record(updatedStockIds.size());
    if (portfolios.isEmpty()) {
      log.info("No portfolios found with updated stock IDs for profit recalculation.");
      sample.stop(profitRecalculationTimer());
      return;
    }

    List<Long> stockIds = portfolios.stream()
      .flatMap(portfolio -> portfolio.getAssets().stream())
      .map(Asset::getStockId)
      .distinct()
      .toList();
    DistributionSummary.builder("asset.profit.recalculation.distinct_stocks")
      .description("수익률 재계산 대상 고유 종목 수")
      .register(meterRegistry)
      .record(stockIds.size());

    if (stockIds.isEmpty()) {
      log.info("No stock IDs found in portfolios for profit recalculation.");
      sample.stop(profitRecalculationTimer());
      return;
    }

    // ② 외부 HTTP 호출 — 커넥션 없음
    List<BatchPriceSnapshot> batchPrices = marketPriceClient.getBatchPrices(stockIds);
    if (batchPrices == null || batchPrices.isEmpty()) {
      log.warn("No batch prices retrieved for stock IDs: {}", stockIds);
      sample.stop(profitRecalculationTimer());
      return;
    }

    Map<Long, BigDecimal> priceByStockId = batchPrices.stream()
      .collect(Collectors.toMap(BatchPriceSnapshot::getStockId, BatchPriceSnapshot::getPrice));

    // ③ 짧은 write 트랜잭션 — valuation 업데이트 후 커넥션 반환
    txHelper.updateValuations(portfolios, priceByStockId);

    Set<UUID> impactedUserIds = portfolios.stream()
      .map(PortfolioGroup::getUserId)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(HashSet::new));

    // ④ 짧은 read 트랜잭션 — 랭킹 집계용 사용자 요약 조회
    List<UserProfitSummaryRow> allSummaries = txHelper.readAllUserProfitSummaries();
    Set<UUID> usersWithAssets = new HashSet<>(txHelper.readUserIdsWithAssets());
    publishUserProfitRatesUpdatedEvent(allSummaries, usersWithAssets, impactedUserIds);
    sample.stop(profitRecalculationTimer());
  }

  private Timer profitRecalculationTimer() {
    return Timer.builder("asset.profit.recalculation.duration")
      .description("수익률 재계산 전체 소요 시간")
      .register(meterRegistry);
  }

  private void publishUserProfitRatesUpdatedEvent(
    List<UserProfitSummaryRow> summaries,
    Set<UUID> usersWithAssets,
    Set<UUID> impactedUserIds
  ) {
    if (summaries == null || summaries.isEmpty() || usersWithAssets == null || usersWithAssets.isEmpty()) {
      return;
    }

    Map<UUID, String> userNamesByIds = getUserNamesByIds(usersWithAssets);
    Map<UUID, CurrentUserProfitData> currentProfitDataByUser =
      buildCurrentProfitData(summaries, usersWithAssets, userNamesByIds);
    List<UserProfitRankingData> rankings = buildDailyRankings(currentProfitDataByUser);
    AllUserProfitRatesUpdatedEvent event = AllUserProfitRatesUpdatedEvent.builder()
      .rankings(rankings)
      .calculatedAt(LocalDateTime.now())
      .build();

    eventPublisher.publishEvent(event);
    userProfitRankingAggregationService.aggregateRollingRankings(LocalDate.now(KST), currentProfitDataByUser);

    publishCurrentReturnRateMetrics(currentProfitDataByUser, impactedUserIds);
  }

  private Map<UUID, String> getUserNamesByIds(Collection<UUID> userIds) {
    Map<UUID, String> userNamesByIds = userNicknameClient.getUserNicknamesByIds(userIds);
    if (userNamesByIds == null) {
      return Map.of();
    }
    return userNamesByIds;
  }

  private Map<UUID, CurrentUserProfitData> buildCurrentProfitData(
    List<UserProfitSummaryRow> summaries,
    Set<UUID> usersWithAssets,
    Map<UUID, String> userNamesByIds
  ) {
    Map<UUID, CurrentUserProfitData> result = new HashMap<>();
    for (UserProfitSummaryRow summary : summaries) {
      UUID userId = summary.userId();
      if (userId == null || !usersWithAssets.contains(userId)) {
        continue;
      }
      result.put(userId, new CurrentUserProfitData(
        userId,
        userNamesByIds.get(userId),
        defaultValue(summary.totalCurrentValue()),
        defaultValue(summary.totalProfitLoss()),
        calculateReturnRate(defaultValue(summary.totalProfitLoss()), purchaseAmountOf(summary))
      ));
    }
    return result;
  }

  private List<UserProfitRankingData> buildDailyRankings(Map<UUID, CurrentUserProfitData> currentProfitDataByUser) {
    List<UserProfitRankingData> rankings = new ArrayList<>();
    for (CurrentUserProfitData currentUserProfitData : currentProfitDataByUser.values()) {
      rankings.add(new UserProfitRankingData(
        currentUserProfitData.userId(),
        currentUserProfitData.userNickname(),
        currentUserProfitData.totalReturnRate(),
        currentUserProfitData.totalProfitLoss()
      ));
    }
    return rankings;
  }

  private void publishCurrentReturnRateMetrics(
    Map<UUID, CurrentUserProfitData> currentProfitDataByUser,
    Set<UUID> impactedUserIds
  ) {
    if (currentProfitDataByUser == null || currentProfitDataByUser.isEmpty()
      || impactedUserIds == null || impactedUserIds.isEmpty()) {
      return;
    }
    Instant occurredAt = Instant.now();
    for (UUID userId : impactedUserIds) {
      CurrentUserProfitData currentUserProfitData = currentProfitDataByUser.get(userId);
      if (currentUserProfitData == null) {
        continue;
      }
      gamificationEventProducer.publishUserMetricUpdatedEvent(UserMetricUpdatedEvent.builder()
        .userId(userId.toString())
        .eventType(MetricEventType.CURRENT_RETURN_RATE_UPDATED)
        .delta(currentUserProfitData.totalReturnRate().doubleValue())
        .occurredAt(occurredAt)
        .build());
    }
  }

  private BigDecimal calculateReturnRate(BigDecimal profitLoss, BigDecimal purchaseAmount) {
    if (purchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    return profitLoss
      .divide(purchaseAmount, 4, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private BigDecimal purchaseAmountOf(UserProfitSummaryRow summary) {
    return defaultValue(summary.totalCurrentValue()).subtract(defaultValue(summary.totalProfitLoss()));
  }

  private BigDecimal defaultValue(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
