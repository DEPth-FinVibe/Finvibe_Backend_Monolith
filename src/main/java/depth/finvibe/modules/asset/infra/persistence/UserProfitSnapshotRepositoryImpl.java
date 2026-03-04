package depth.finvibe.modules.asset.infra.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.UserProfitSnapshotRepository;
import depth.finvibe.modules.asset.domain.UserProfitSnapshotDaily;

@Repository
@RequiredArgsConstructor
public class UserProfitSnapshotRepositoryImpl implements UserProfitSnapshotRepository {
  private final UserProfitSnapshotDailyJpaRepository jpaRepository;

  @Override
  public void saveAll(List<UserProfitSnapshotDaily> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return;
    }
    jpaRepository.saveAll(snapshots);
  }

  @Override
  public List<UserProfitSnapshotDaily> findBySnapshotDate(LocalDate snapshotDate) {
    if (snapshotDate == null) {
      return List.of();
    }
    return jpaRepository.findByIdSnapshotDate(snapshotDate);
  }

  @Override
  public boolean existsPositiveProfitSnapshot(UUID userId, BigDecimal minimumProfit, LocalDate beforeDate) {
    if (userId == null || minimumProfit == null || beforeDate == null) {
      return false;
    }
    return jpaRepository.existsByIdUserIdAndTotalProfitLossGreaterThanAndIdSnapshotDateLessThan(
      userId,
      minimumProfit,
      beforeDate
    );
  }
}
