package depth.finvibe.modules.asset.infra.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.asset.domain.UserProfitSnapshotDaily;
import depth.finvibe.modules.asset.domain.UserProfitSnapshotDailyId;

public interface UserProfitSnapshotDailyJpaRepository
  extends JpaRepository<UserProfitSnapshotDaily, UserProfitSnapshotDailyId> {
  List<UserProfitSnapshotDaily> findByIdSnapshotDate(LocalDate snapshotDate);

  boolean existsByIdUserIdAndTotalProfitLossGreaterThanAndIdSnapshotDateLessThan(
    UUID userId,
    BigDecimal minimumProfit,
    LocalDate snapshotDate
  );
}
