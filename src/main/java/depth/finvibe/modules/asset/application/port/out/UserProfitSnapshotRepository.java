package depth.finvibe.modules.asset.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import depth.finvibe.modules.asset.domain.UserProfitSnapshotDaily;

public interface UserProfitSnapshotRepository {
  void saveAll(List<UserProfitSnapshotDaily> snapshots);

  List<UserProfitSnapshotDaily> findBySnapshotDate(LocalDate snapshotDate);

  boolean existsPositiveProfitSnapshot(UUID userId, BigDecimal minimumProfit, LocalDate beforeDate);
}
