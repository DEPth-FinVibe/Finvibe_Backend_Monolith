package depth.finvibe.modules.market.infra.client;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.ChkHolidayClient;
import depth.finvibe.modules.market.domain.HolidayDayInfo;
import depth.finvibe.modules.market.infra.client.dto.KisDto;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChkHolidayClientImpl implements ChkHolidayClient {

  private static final DateTimeFormatter BASS_DT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final int MAX_PAGES = 3;

  private final KisApiClient kisApiClient;

  @Override
  public List<HolidayDayInfo> fetchChkHoliday(YearMonth yearMonth) {
    int expectedDays = yearMonth.lengthOfMonth();
    LocalDate startDate = yearMonth.atDay(1);

    List<KisDto.ChkHolidayOutput> allOutput = new ArrayList<>();
    Set<LocalDate> collectedDates = new LinkedHashSet<>();
    LocalDate nextFetchDate = startDate;
    int pageCount = 0;

    while (pageCount < MAX_PAGES) {
      String fetchBassDt = formatBassDt(nextFetchDate);

      try {
        List<KisDto.ChkHolidayOutput> output = kisApiClient.fetchChkHoliday(fetchBassDt);

        if (output.isEmpty()) {
          break;
        }

        allOutput.addAll(output);
        pageCount++;

        // 해당 월의 날짜 개수 계산 (다음 달 데이터는 제외)
        for (KisDto.ChkHolidayOutput item : output) {
          LocalDate date = parseBassDt(item.getBass_dt());
          if (YearMonth.from(date).equals(yearMonth)) {
            collectedDates.add(date);
          }
        }

        // 해당 월의 모든 날짜를 수집했으면 종료
        if (collectedDates.size() >= expectedDays) {
          break;
        }

        // 다음 페이지 날짜 계산
        LocalDate lastDate = getLastDateFromResponse(output);
        nextFetchDate = lastDate.plusDays(1);

      } catch (DomainException e) {
        if (allOutput.isEmpty()) {
          throw e;
        } else {
          log.warn("페이징 중간 호출 실패. 지금까지 수집된 데이터 {}개 반환. bassDt={}",
              collectedDates.size(), fetchBassDt, e);
          break;
        }
      }
    }

    log.info("휴장일 달력 API 호출 완료. yearMonth={}, pages={}, yearMonthRecords={}, totalRecords={}",
        yearMonth, pageCount, collectedDates.size(), allOutput.size());

    // 해당 월 데이터만 필터링하여 반환
    return allOutput.stream()
        .filter(output -> {
          LocalDate date = parseBassDt(output.getBass_dt());
          return YearMonth.from(date).equals(yearMonth);
        })
        .map(this::toHolidayDayInfo)
        .toList();
  }

  private LocalDate parseBassDt(String bassDt) {
    return LocalDate.parse(bassDt, BASS_DT_FORMAT);
  }

  private String formatBassDt(LocalDate date) {
    return date.format(BASS_DT_FORMAT);
  }

  private LocalDate getLastDateFromResponse(List<KisDto.ChkHolidayOutput> output) {
    return parseBassDt(output.get(output.size() - 1).getBass_dt());
  }

  private HolidayDayInfo toHolidayDayInfo(KisDto.ChkHolidayOutput o) {
    LocalDate date = LocalDate.parse(o.getBass_dt(), BASS_DT_FORMAT);
    boolean openDay = "Y".equalsIgnoreCase(o.getOpnd_yn());
    return new HolidayDayInfo(date, openDay);
  }
}
