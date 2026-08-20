package depth.finvibe.modules.market.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.out.ChkHolidayClient;
import depth.finvibe.modules.market.application.port.out.TradingDayRepository;
import depth.finvibe.modules.market.domain.HolidayDayInfo;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.TradingDay;
import depth.finvibe.modules.market.domain.enums.TradingDayStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayCalendarService {

  private static final DateTimeFormatter BASS_DT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final int MISSING_DATES_LOG_LIMIT = 10;

  private final TradingDayRepository tradingDayRepository;
  private final ChkHolidayClient chkHolidayClient;
  private final MeterRegistry meterRegistry;

  /**
   * 특정 날짜의 개장 여부를 반환한다. 저장 데이터와 외부 동기화로도 확인할 수 없으면 UNKNOWN이다.
   */
  public TradingDayStatus getTradingDayStatus(LocalDate date) {
    Optional<Boolean> saved = tradingDayRepository.findOpenDay(date);
    if (saved.isPresent()) {
      return saved.get() ? TradingDayStatus.OPEN : TradingDayStatus.CLOSED;
    }

    ensureCalendarForMonth(YearMonth.from(date));
    return tradingDayRepository.findOpenDay(date)
        .map(openDay -> openDay ? TradingDayStatus.OPEN : TradingDayStatus.CLOSED)
        .orElseGet(() -> {
          meterRegistry.counter("market.calendar.status.unknown").increment();
          log.error("휴장일 달력으로 거래 여부를 판정할 수 없습니다. date={}", date);
          return TradingDayStatus.UNKNOWN;
        });
  }

  /**
   * 해당 일 이하 중 가장 최근 개장일을 반환.
   * 해당 연월(및 필요 시 이전 달) 데이터가 없으면 KIS 호출로 적재 후 조회.
   */
  public Optional<LocalDate> getLastTradingDayOnOrBefore(LocalDate date) {
    YearMonth month = YearMonth.from(date);
    if (!isCalendarComplete(month)) {
      ensureCalendarForMonth(month);
    }
    Optional<LocalDate> last = tradingDayRepository.findLastOpenDayOnOrBefore(date);
    if (last.isEmpty()) {
      YearMonth prevMonth = month.minusMonths(1);
      if (!isCalendarComplete(prevMonth)) {
        ensureCalendarForMonth(prevMonth);
      }
      last = tradingDayRepository.findLastOpenDayOnOrBefore(date);
    }
    return last;
  }

  /**
   * 해당 시각까지 장 마감이 완료된 가장 최근 거래일을 반환한다.
   * 개장일이라도 15:30 이전이면 당일 종가가 아직 확정되지 않았으므로 전 거래일부터 조회한다.
   */
  public Optional<LocalDate> getLastCompletedTradingDay(LocalDateTime dateTime) {
    LocalDate candidateDate = MarketHours.isSessionCompletedAt(dateTime.toLocalTime())
        ? dateTime.toLocalDate()
        : dateTime.toLocalDate().minusDays(1);
    return getLastTradingDayOnOrBefore(candidateDate);
  }

  /**
   * 해당 연월의 휴장일 달력이 DB에 없으면 KIS 국내휴장일조회로 적재.
   */
  public boolean ensureCalendarForMonth(YearMonth yearMonth) {
    if (isCalendarComplete(yearMonth)) {
      return true;
    }
    try {
      List<HolidayDayInfo> infos = chkHolidayClient.fetchChkHoliday(yearMonth);
      List<TradingDay> tradingDays = infos.stream()
          .collect(Collectors.toMap(
              HolidayDayInfo::date,
              HolidayDayInfo::openDay,
              (existing, replacement) -> existing,
              LinkedHashMap::new
          ))
          .entrySet().stream()
          .map(entry -> TradingDay.of(entry.getKey(), entry.getValue()))
          .toList();

      List<LocalDate> missingDates = calculateMissingDates(yearMonth, infos);

      if (!tradingDays.isEmpty()) {
        tradingDayRepository.saveAll(tradingDays);
        log.info("휴장일 달력 적재 완료. yearMonth={}, count={}", yearMonth, tradingDays.size());
      }
      if (!isCalendarComplete(yearMonth)) {
        long actualCount = tradingDayRepository.countByYearMonth(yearMonth.getYear(), yearMonth.getMonthValue());
        if (!missingDates.isEmpty()) {
          log.warn(
              "휴장일 달력 누락 날짜 감지. yearMonth={}, missingCount={}, missingDates={}",
              yearMonth,
              missingDates.size(),
              formatMissingDatesForLog(missingDates)
          );
        }
        log.warn("휴장일 달력 부분 적재 상태. yearMonth={}, expectedCount={}, actualCount={}",
            yearMonth,
            yearMonth.lengthOfMonth(),
            actualCount);
      }
      return isCalendarComplete(yearMonth);
    } catch (Exception e) {
      meterRegistry.counter("market.calendar.sync.failures").increment();
      log.warn("휴장일 조회 실패. yearMonth={}", yearMonth, e);
      return false;
    }
  }

  private boolean isCalendarComplete(YearMonth yearMonth) {
    long savedCount = tradingDayRepository.countByYearMonth(yearMonth.getYear(), yearMonth.getMonthValue());
    return savedCount >= yearMonth.lengthOfMonth();
  }

  private List<LocalDate> calculateMissingDates(YearMonth yearMonth, List<HolidayDayInfo> infos) {
    Set<LocalDate> loadedDates = infos.stream()
        .map(HolidayDayInfo::date)
        .filter(date -> YearMonth.from(date).equals(yearMonth))
        .collect(Collectors.toSet());

    List<LocalDate> missingDates = new ArrayList<>();
    for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
      LocalDate date = yearMonth.atDay(day);
      if (!loadedDates.contains(date)) {
        missingDates.add(date);
      }
    }
    return missingDates;
  }

  private String formatMissingDatesForLog(List<LocalDate> missingDates) {
    if (missingDates.size() <= MISSING_DATES_LOG_LIMIT) {
      return missingDates.toString();
    }
    List<LocalDate> truncated = new ArrayList<>(missingDates.subList(0, MISSING_DATES_LOG_LIMIT));
    return truncated + " ...";
  }
}
