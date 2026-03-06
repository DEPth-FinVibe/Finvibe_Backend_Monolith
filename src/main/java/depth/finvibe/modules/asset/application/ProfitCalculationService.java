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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.asset.application.event.AllUserProfitRatesUpdatedEvent;
import depth.finvibe.modules.asset.application.port.in.ProfitCalculationUseCase;
import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingData;
import depth.finvibe.modules.asset.domain.Asset;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.infra.client.MarketInternalClient;
import depth.finvibe.modules.asset.infra.client.UserNicknameClientImpl;
import depth.finvibe.common.investment.application.port.out.GamificationEventProducer;
import depth.finvibe.common.investment.dto.BatchPriceSnapshot;
import depth.finvibe.common.investment.dto.MetricEventType;
import depth.finvibe.common.investment.dto.UserMetricUpdatedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitCalculationService implements ProfitCalculationUseCase {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final PortfolioGroupRepository portfolioGroupRepository;
  private final MarketInternalClient marketInternalClient;
  private final UserNicknameClientImpl userNicknameClient;
  private final UserProfitRankingAggregationService userProfitRankingAggregationService;
  private final ApplicationEventPublisher eventPublisher;
  private final GamificationEventProducer gamificationEventProducer;

  @Override
  @Transactional
  public void recalculateAllProfits(List<Long> updatedStockIds) {
    if (updatedStockIds == null || updatedStockIds.isEmpty()) {
      return;
    }

    List<PortfolioGroup> portfolios = portfolioGroupRepository.findAllByStockIdsWithAssets(updatedStockIds);
    if (portfolios.isEmpty()) {
      log.info("No portfolios found with updated stock IDs for profit recalculation.");
      List<PortfolioGroup> allPortfolios = portfolioGroupRepository.findAllWithAssets();
      publishUserProfitRatesUpdatedEvent(allPortfolios);
      return;
    }

    List<Long> stockIds = portfolios.stream()
      .flatMap(portfolio -> portfolio.getAssets().stream())
      .map(Asset::getStockId)
      .distinct()
      .toList();

    if (stockIds.isEmpty()) {
      log.info("No stock IDs found in portfolios for profit recalculation.");
      return;
    }

    List<BatchPriceSnapshot> batchPrices = marketInternalClient.getBatchPrices(stockIds);
    if (batchPrices == null || batchPrices.isEmpty()) {
      log.warn("No batch prices retrieved for stock IDs: {}", stockIds);
      return;
    }

    Map<Long, BigDecimal> priceByStockId = batchPrices.stream()
      .collect(Collectors.toMap(BatchPriceSnapshot::getStockId, BatchPriceSnapshot::getPrice));

    for (PortfolioGroup portfolio : portfolios) {
      for (Asset asset : portfolio.getAssets()) {
        BigDecimal currentPrice = priceByStockId.get(asset.getStockId());
        if (currentPrice != null) {
          asset.updateValuation(currentPrice);
        }
      }
      portfolio.recalculateValuation();
    }

    List<PortfolioGroup> allPortfolios = portfolioGroupRepository.findAllWithAssets();
    publishUserProfitRatesUpdatedEvent(allPortfolios);
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
