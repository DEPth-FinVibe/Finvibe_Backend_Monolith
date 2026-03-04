package depth.finvibe.modules.asset.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.asset.domain.PortfolioPerformanceSnapshotDaily;

public interface PortfolioPerformanceSnapshotRepository {
  void saveAll(List<PortfolioPerformanceSnapshotDaily> snapshots);

  List<PortfolioPerformanceSnapshotDaily> findByUserIdAndSnapshotDateBetween(
    UUID userId,
    LocalDate startDate,
    LocalDate endDate
  );
}
