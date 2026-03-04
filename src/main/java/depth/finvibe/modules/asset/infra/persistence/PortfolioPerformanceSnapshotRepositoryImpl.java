package depth.finvibe.modules.asset.infra.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.PortfolioPerformanceSnapshotRepository;
import depth.finvibe.modules.asset.domain.PortfolioPerformanceSnapshotDaily;

@Repository
@RequiredArgsConstructor
public class PortfolioPerformanceSnapshotRepositoryImpl implements PortfolioPerformanceSnapshotRepository {
  private final PortfolioPerformanceSnapshotDailyJpaRepository jpaRepository;

  @Override
  public void saveAll(List<PortfolioPerformanceSnapshotDaily> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return;
    }
    jpaRepository.saveAll(snapshots);
  }

  @Override
  public List<PortfolioPerformanceSnapshotDaily> findByUserIdAndSnapshotDateBetween(
    UUID userId,
    LocalDate startDate,
    LocalDate endDate
  ) {
    if (userId == null || startDate == null || endDate == null || startDate.isAfter(endDate)) {
      return List.of();
    }

    return jpaRepository.findByUserIdAndIdSnapshotDateBetween(userId, startDate, endDate);
  }
}
