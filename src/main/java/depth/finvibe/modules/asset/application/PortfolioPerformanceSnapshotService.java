package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.asset.application.port.out.PortfolioGroupRepository;
import depth.finvibe.modules.asset.application.port.out.PortfolioPerformanceSnapshotRepository;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.domain.PortfolioPerformanceSnapshotDaily;
import depth.finvibe.modules.asset.domain.PortfolioPerformanceSnapshotDailyId;
import depth.finvibe.modules.asset.domain.PortfolioValuation;

@Service
@RequiredArgsConstructor
public class PortfolioPerformanceSnapshotService {
  private final PortfolioGroupRepository portfolioGroupRepository;
  private final PortfolioPerformanceSnapshotRepository portfolioPerformanceSnapshotRepository;

  @Transactional
  public void saveDailySnapshot(LocalDate snapshotDate) {
    if (snapshotDate == null) {
      return;
    }

    List<PortfolioGroup> portfolios = portfolioGroupRepository.findAllWithAssets();
    if (portfolios == null || portfolios.isEmpty()) {
      return;
    }

    List<PortfolioPerformanceSnapshotDaily> snapshots = portfolios.stream()
      .filter(portfolio -> portfolio.getId() != null)
      .map(portfolio -> toSnapshot(portfolio, snapshotDate))
      .toList();

    portfolioPerformanceSnapshotRepository.saveAll(snapshots);
  }

  private PortfolioPerformanceSnapshotDaily toSnapshot(PortfolioGroup portfolio, LocalDate snapshotDate) {
    PortfolioValuation valuation = portfolio.getValuation();
    BigDecimal totalCurrentValue = BigDecimal.ZERO;
    BigDecimal totalProfitLoss = BigDecimal.ZERO;
    BigDecimal totalReturnRate = BigDecimal.ZERO;

    if (valuation != null) {
      totalCurrentValue = valuation.getTotalCurrentValue() != null ? valuation.getTotalCurrentValue() : BigDecimal.ZERO;
      totalProfitLoss = valuation.getTotalProfitLoss() != null ? valuation.getTotalProfitLoss() : BigDecimal.ZERO;
      totalReturnRate = valuation.getTotalReturnRate() != null ? valuation.getTotalReturnRate() : BigDecimal.ZERO;
    }

    return PortfolioPerformanceSnapshotDaily.create(
      new PortfolioPerformanceSnapshotDailyId(portfolio.getId(), snapshotDate),
      portfolio.getUserId(),
      portfolio.getName(),
      totalCurrentValue,
      totalProfitLoss,
      totalReturnRate
    );
  }
}
