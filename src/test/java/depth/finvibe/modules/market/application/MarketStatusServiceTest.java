package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.domain.enums.MarketStatusReason;
import depth.finvibe.modules.market.domain.enums.TradingDayStatus;
import depth.finvibe.modules.market.dto.MarketStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketStatusServiceTest {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

  @Mock
  private HolidayCalendarService holidayCalendarService;

  private MarketStatusService service;

  @BeforeEach
  void setUp() {
    service = new MarketStatusService(holidayCalendarService);
  }

  @Test
  @DisplayName("개장일의 장중 시각은 OPEN으로 판정한다")
  void getMarketStatusAt_tradingHours_open() {
    LocalDate date = LocalDate.of(2026, 8, 21);
    when(holidayCalendarService.getTradingDayStatus(date)).thenReturn(TradingDayStatus.OPEN);

    MarketStatusDto.Response response = service.getMarketStatusAt(
        ZonedDateTime.of(2026, 8, 21, 10, 0, 0, 0, KOREA_ZONE)
    );

    assertThat(response.getStatus()).isEqualTo(MarketStatus.OPEN);
    assertThat(response.getReason()).isEqualTo(MarketStatusReason.TRADING_HOURS);
    assertThat(response.getTradingDate()).isEqualTo(date);
  }

  @Test
  @DisplayName("평일 휴장일은 장중 시각에도 CLOSED로 판정한다")
  void getMarketStatusAt_holiday_closed() {
    LocalDate date = LocalDate.of(2026, 8, 17);
    LocalDate lastTradingDay = LocalDate.of(2026, 8, 14);
    when(holidayCalendarService.getTradingDayStatus(date)).thenReturn(TradingDayStatus.CLOSED);
    when(holidayCalendarService.getLastCompletedTradingDay(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(lastTradingDay));

    MarketStatusDto.Response response = service.getMarketStatusAt(
        ZonedDateTime.of(2026, 8, 17, 10, 0, 0, 0, KOREA_ZONE)
    );

    assertThat(response.getStatus()).isEqualTo(MarketStatus.CLOSED);
    assertThat(response.getReason()).isEqualTo(MarketStatusReason.HOLIDAY);
    assertThat(response.getTradingDate()).isEqualTo(lastTradingDay);
  }

  @Test
  @DisplayName("달력을 확인할 수 없으면 CLOSED로 안전하게 판정한다")
  void getMarketStatusAt_calendarUnavailable_failClosed() {
    LocalDate date = LocalDate.of(2026, 8, 21);
    when(holidayCalendarService.getTradingDayStatus(date)).thenReturn(TradingDayStatus.UNKNOWN);

    MarketStatusDto.Response response = service.getMarketStatusAt(
        ZonedDateTime.of(2026, 8, 21, 10, 0, 0, 0, KOREA_ZONE)
    );

    assertThat(response.getStatus()).isEqualTo(MarketStatus.CLOSED);
    assertThat(response.getReason()).isEqualTo(MarketStatusReason.CALENDAR_UNAVAILABLE);
    assertThat(response.getTradingDate()).isNull();
  }

  @Test
  @DisplayName("주말은 외부 달력 상태 조회 없이 CLOSED로 판정한다")
  void getMarketStatusAt_weekend_closed() {
    LocalDate date = LocalDate.of(2026, 8, 22);
    when(holidayCalendarService.getLastCompletedTradingDay(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(LocalDate.of(2026, 8, 21)));

    MarketStatusDto.Response response = service.getMarketStatusAt(
        ZonedDateTime.of(2026, 8, 22, 10, 0, 0, 0, KOREA_ZONE)
    );

    assertThat(response.getStatus()).isEqualTo(MarketStatus.CLOSED);
    assertThat(response.getReason()).isEqualTo(MarketStatusReason.WEEKEND);
  }
}
