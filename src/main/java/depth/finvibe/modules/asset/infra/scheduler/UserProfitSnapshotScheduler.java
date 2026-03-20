package depth.finvibe.modules.asset.infra.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.UserProfitSnapshotService;

@Component
@RequiredArgsConstructor
public class UserProfitSnapshotScheduler {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final UserProfitSnapshotService userProfitSnapshotService;

  public void saveDailySnapshot() {
    LocalDate snapshotDate = LocalDate.now(KST).minusDays(1);
    userProfitSnapshotService.saveDailySnapshot(snapshotDate);
  }
}
