package depth.finvibe.modules.market.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.out.ChkHolidayClient;
import depth.finvibe.modules.market.application.port.out.TradingDayRepository;
import depth.finvibe.modules.market.domain.HolidayDayInfo;
import depth.finvibe.modules.market.domain.TradingDay;
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
   * 해당 연월의 휴장일 달력이 DB에 없으면 KIS 국내휴장일조회로 적재.
   */
  public void ensureCalendarForMonth(YearMonth yearMonth) {
    if (isCalendarComplete(yearMonth)) {
      return;
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
    } catch (Exception e) {
      log.warn("휴장일 조회 실패. yearMonth={}", yearMonth, e);
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
