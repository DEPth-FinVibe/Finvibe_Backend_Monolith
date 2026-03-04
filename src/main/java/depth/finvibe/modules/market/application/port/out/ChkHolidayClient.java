package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.HolidayDayInfo;

import java.time.YearMonth;
import java.util.List;

/**
 * KIS 국내휴장일조회(chk_holiday) API 호출 포트.
 */
public interface ChkHolidayClient {

  /**
   * 지정한 연월의 모든 휴장일 정보를 조회.
   * 내부적으로 KIS API를 여러 번 호출(페이징)하여 해당 월의 전체 데이터를 수집합니다.
   *
   * @param yearMonth 조회할 연월
   * @return 해당 월의 모든 휴장일 정보 리스트 (date, openDay)
   */
  List<HolidayDayInfo> fetchChkHoliday(YearMonth yearMonth);
}
