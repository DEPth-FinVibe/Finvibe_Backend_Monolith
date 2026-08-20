package depth.finvibe.modules.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import depth.finvibe.modules.market.application.port.out.ChkHolidayClient;
import depth.finvibe.modules.market.application.port.out.TradingDayRepository;
import depth.finvibe.modules.market.domain.enums.TradingDayStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HolidayCalendarServiceTest {

  @Mock
  private TradingDayRepository tradingDayRepository;
  @Mock
  private ChkHolidayClient chkHolidayClient;

  private SimpleMeterRegistry meterRegistry;
  private HolidayCalendarService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new HolidayCalendarService(tradingDayRepository, chkHolidayClient, meterRegistry);
  }

  @Test
  @DisplayName("저장된 거래일 정보를 우선 사용한다")
  void getTradingDayStatus_cached_open() {
    LocalDate date = LocalDate.of(2026, 8, 21);
    when(tradingDayRepository.findOpenDay(date)).thenReturn(Optional.of(true));

    TradingDayStatus status = service.getTradingDayStatus(date);

    assertThat(status).isEqualTo(TradingDayStatus.OPEN);
  }

  @Test
  @DisplayName("달력 동기화가 실패하고 저장 정보도 없으면 UNKNOWN을 반환한다")
  void getTradingDayStatus_syncFailure_unknown() {
    LocalDate date = LocalDate.of(2026, 8, 21);
    YearMonth yearMonth = YearMonth.from(date);
    when(tradingDayRepository.findOpenDay(date)).thenReturn(Optional.empty());
    when(tradingDayRepository.countByYearMonth(yearMonth.getYear(), yearMonth.getMonthValue()))
        .thenReturn(0L);
    when(chkHolidayClient.fetchChkHoliday(yearMonth)).thenThrow(new IllegalStateException("KIS unavailable"));

    TradingDayStatus status = service.getTradingDayStatus(date);

    assertThat(status).isEqualTo(TradingDayStatus.UNKNOWN);
    assertThat(meterRegistry.counter("market.calendar.sync.failures").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter("market.calendar.status.unknown").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("장 마감 전에는 전 거래일을 최근 완료 거래일로 조회한다")
  void getLastCompletedTradingDay_beforeClose_usesPreviousDate() {
    LocalDate previousTradingDay = LocalDate.of(2026, 8, 20);
    when(tradingDayRepository.countByYearMonth(2026, 8)).thenReturn(31L);
    when(tradingDayRepository.findLastOpenDayOnOrBefore(LocalDate.of(2026, 8, 20)))
        .thenReturn(Optional.of(previousTradingDay));

    Optional<LocalDate> result = service.getLastCompletedTradingDay(
        LocalDateTime.of(2026, 8, 21, 8, 30)
    );

    assertThat(result).contains(previousTradingDay);
  }
}
