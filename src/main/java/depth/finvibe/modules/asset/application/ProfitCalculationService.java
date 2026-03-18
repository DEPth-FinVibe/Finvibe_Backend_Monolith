package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
      List<PortfolioGroup> allPortfolios = txHelper.readAllPortfolios();
      publishUserProfitRatesUpdatedEvent(allPortfolios);
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

    // ④ 짧은 read 트랜잭션 — 랭킹 집계용 전체 조회
    List<PortfolioGroup> allPortfolios = txHelper.readAllPortfolios();
    publishUserProfitRatesUpdatedEvent(allPortfolios);
    sample.stop(profitRecalculationTimer());
  }

  private Timer profitRecalculationTimer() {
    return Timer.builder("asset.profit.recalculation.duration")
      .description("수익률 재계산 전체 소요 시간")
      .register(meterRegistry);
  }

  private void publishUserProfitRatesUpdatedEvent(List<PortfolioGroup> portfolios) {
    if (portfolios == null || portfolios.isEmpty()) {
      return;
    }

    Map<UUID, List<PortfolioGroup>> portfoliosByUser = portfolios.stream()
      .collect(Collectors.groupingBy(PortfolioGroup::getUserId));

    Map<UUID, UserProfitSummary> summaries = buildUserSummaries(portfoliosByUser);
    Map<UUID, String> userNamesByIds = getUserNamesByIds(summaries.keySet());
    Map<UUID, CurrentUserProfitData> currentProfitDataByUser = buildCurrentProfitData(summaries, userNamesByIds);
    List<UserProfitRankingData> rankings = buildDailyRankings(currentProfitDataByUser);
    AllUserProfitRatesUpdatedEvent event = AllUserProfitRatesUpdatedEvent.builder()
      .rankings(rankings)
      .calculatedAt(LocalDateTime.now())
      .build();

    eventPublisher.publishEvent(event);
    userProfitRankingAggregationService.aggregateRollingRankings(LocalDate.now(KST), currentProfitDataByUser);

    publishCurrentReturnRateMetrics(summaries);
  }

  private Map<UUID, UserProfitSummary> buildUserSummaries(Map<UUID, List<PortfolioGroup>> portfoliosByUser) {
    Map<UUID, UserProfitSummary> summaries = new HashMap<>();
    for (Map.Entry<UUID, List<PortfolioGroup>> entry : portfoliosByUser.entrySet()) {
      summaries.put(entry.getKey(), calculateUserProfitSummary(entry.getValue()));
    }
    return summaries;
  }

  private Map<UUID, String> getUserNamesByIds(Collection<UUID> userIds) {
    Map<UUID, String> userNamesByIds = userNicknameClient.getUserNicknamesByIds(userIds);
    if (userNamesByIds == null) {
      return Map.of();
    }
    return userNamesByIds;
  }

  private Map<UUID, CurrentUserProfitData> buildCurrentProfitData(
    Map<UUID, UserProfitSummary> summaries,
    Map<UUID, String> userNamesByIds
  ) {
    Map<UUID, CurrentUserProfitData> result = new HashMap<>();
    for (Map.Entry<UUID, UserProfitSummary> entry : summaries.entrySet()) {
      UserProfitSummary summary = entry.getValue();
      if (!summary.hasAssets()) {
        continue;
      }
      UUID userId = entry.getKey();
      result.put(userId, new CurrentUserProfitData(
        userId,
        userNamesByIds.get(userId),
        summary.totalCurrentValue(),
        summary.totalProfitLoss(),
        summary.totalReturnRate()
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

  private void publishCurrentReturnRateMetrics(Map<UUID, UserProfitSummary> summaries) {
    if (summaries == null || summaries.isEmpty()) {
      return;
    }
    Instant occurredAt = Instant.now();
    for (Map.Entry<UUID, UserProfitSummary> entry : summaries.entrySet()) {
      UserProfitSummary summary = entry.getValue();
      if (!summary.hasAssets()) {
        continue;
      }
      gamificationEventProducer.publishUserMetricUpdatedEvent(UserMetricUpdatedEvent.builder()
        .userId(entry.getKey().toString())
        .eventType(MetricEventType.CURRENT_RETURN_RATE_UPDATED)
        .delta(summary.totalReturnRate().doubleValue())
        .occurredAt(occurredAt)
        .build());
    }
  }

  private UserProfitSummary calculateUserProfitSummary(List<PortfolioGroup> portfolios) {
    boolean hasAssets = portfolios.stream()
      .flatMap(portfolio -> portfolio.getAssets().stream())
      .findAny()
      .isPresent();

    if (!hasAssets) {
      return new UserProfitSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    BigDecimal totalCurrentValue = portfolios.stream()
      .map(PortfolioGroup::getValuation)
      .filter(Objects::nonNull)
      .map(valuation -> Objects.requireNonNullElse(valuation.getTotalCurrentValue(), BigDecimal.ZERO))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalProfitLoss = portfolios.stream()
      .map(PortfolioGroup::getValuation)
      .filter(Objects::nonNull)
      .map(valuation -> Objects.requireNonNullElse(valuation.getTotalProfitLoss(), BigDecimal.ZERO))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal purchaseAmount = totalCurrentValue.subtract(totalProfitLoss);
    BigDecimal totalReturnRate = calculateReturnRate(totalProfitLoss, purchaseAmount);

    return new UserProfitSummary(totalCurrentValue, totalProfitLoss, totalReturnRate, true);
  }

  private BigDecimal calculateReturnRate(BigDecimal profitLoss, BigDecimal purchaseAmount) {
    if (purchaseAmount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    return profitLoss
      .divide(purchaseAmount, 4, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private record UserProfitSummary(
    BigDecimal totalCurrentValue,
    BigDecimal totalProfitLoss,
    BigDecimal totalReturnRate,
    boolean hasAssets
  ) {
  }
}
