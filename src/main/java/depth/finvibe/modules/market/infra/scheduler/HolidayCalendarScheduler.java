package depth.finvibe.modules.market.infra.scheduler;

import java.time.YearMonth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.HolidayCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayCalendarScheduler {

  private final HolidayCalendarService holidayCalendarService;

  /**
   * 매월 1일 새벽 2시에 다음 달 휴장일 데이터 선행 적재.
   */
  @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
  @SchedulerLock(
      name = "holidayCalendarScheduler",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT1M"
  )
  public void ensureNextMonthHolidayCalendar() {
    YearMonth nextMonth = YearMonth.now().plusMonths(1);
    log.info("휴장일 달력 배치 시작. nextMonth={}", nextMonth);
    try {
      holidayCalendarService.ensureCalendarForMonth(nextMonth);
      log.info("휴장일 달력 배치 완료. nextMonth={}", nextMonth);
    } catch (Exception e) {
      log.error("휴장일 달력 배치 실패. nextMonth={}", nextMonth, e);
    }
  }
}
