package depth.finvibe.modules.market.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import depth.finvibe.modules.market.domain.enums.MarketStatus;

public final class MarketHours {
  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 0);
  private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);

  private MarketHours() {
  }

  public static MarketStatus getCurrentStatus() {
    return getStatusAt(ZonedDateTime.now(KOREA_ZONE));
  }

  public static MarketStatus getStatusAt(ZonedDateTime now) {
    // TODO: 한국 공휴일 및 거래소 휴장일 반영 필요
    DayOfWeek dayOfWeek = now.getDayOfWeek();
    if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
      return MarketStatus.CLOSED;
    }

    LocalTime time = now.toLocalTime();
    boolean isOpen = !time.isBefore(MARKET_OPEN_TIME) && time.isBefore(MARKET_CLOSE_TIME);
    return isOpen ? MarketStatus.OPEN : MarketStatus.CLOSED;
  }
}
