package depth.finvibe.modules.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 국내 거래소 휴장일/개장일 정보.
 * KIS 국내휴장일조회(chk_holiday) API의 opnd_yn(개장일여부)을 저장한다.
 */
@Entity
@Table(
    name = "trading_day",
    indexes = {
        @Index(name = "idx_trading_day_date", columnList = "date")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TradingDay {

  @Id
  @Column(name = "date", nullable = false, unique = true)
  private LocalDate date;

  @Column(name = "open_day", nullable = false)
  private Boolean openDay;

  public static TradingDay of(LocalDate date, boolean openDay) {
    return TradingDay.builder()
        .date(date)
        .openDay(openDay)
        .build();
  }
}
