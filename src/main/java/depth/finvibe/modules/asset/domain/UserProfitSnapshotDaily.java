package depth.finvibe.modules.asset.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
  name = "user_profit_snapshot_daily",
  indexes = {
    @Index(name = "idx_user_profit_snapshot_date", columnList = "snapshot_date"),
    @Index(name = "idx_user_profit_snapshot_user_id", columnList = "user_id")
  }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserProfitSnapshotDaily {
  @EmbeddedId
  private UserProfitSnapshotDailyId id;

  @Column(name = "total_current_value", nullable = false, precision = 20, scale = 2)
  private BigDecimal totalCurrentValue;

  @Column(name = "total_profit_loss", nullable = false, precision = 20, scale = 2)
  private BigDecimal totalProfitLoss;

  @Column(name = "total_return_rate", nullable = false, precision = 10, scale = 4)
  private BigDecimal totalReturnRate;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static UserProfitSnapshotDaily create(
    UserProfitSnapshotDailyId id,
    BigDecimal totalCurrentValue,
    BigDecimal totalProfitLoss,
    BigDecimal totalReturnRate
  ) {
    return UserProfitSnapshotDaily.builder()
      .id(id)
      .totalCurrentValue(totalCurrentValue)
      .totalProfitLoss(totalProfitLoss)
      .totalReturnRate(totalReturnRate)
      .updatedAt(LocalDateTime.now())
      .build();
  }
}
