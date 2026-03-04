package depth.finvibe.modules.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "closing_price",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stock_id", "trading_date"})
    },
    indexes = {
        @Index(name = "idx_closing_price_date_stock", columnList = "trading_date,stock_id")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class ClosingPrice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "stock_id", nullable = false)
  private Long stockId;

  @Column(name = "trading_date", nullable = false)
  private LocalDate tradingDate;

  @Column(nullable = false)
  private LocalDateTime at;

  @Column(nullable = false)
  private BigDecimal close;

  @Column(nullable = true)
  private BigDecimal prevDayChangePct;

  @Column(nullable = true)
  private BigDecimal volume;

  @Column(name = "`value`", nullable = true)
  private BigDecimal value;

  public static ClosingPrice create(
      Long stockId,
      LocalDate tradingDate,
      LocalDateTime at,
      BigDecimal close,
      BigDecimal prevDayChangePct,
      BigDecimal volume,
      BigDecimal value
  ) {
    return ClosingPrice.builder()
        .stockId(stockId)
        .tradingDate(tradingDate)
        .at(at)
        .close(close)
        .prevDayChangePct(prevDayChangePct)
        .volume(volume)
        .value(value)
        .build();
  }
}
