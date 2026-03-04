package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.TradingDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TradingDayRepository {

  void saveAll(List<TradingDay> tradingDays);

  /**
   * 해당 일 이하 중 가장 최근 개장일 한 건 반환.
   */
  Optional<LocalDate> findLastOpenDayOnOrBefore(LocalDate date);

  /**
   * 해당 연월에 저장된 거래일 레코드 수.
   */
  long countByYearMonth(int year, int month);
}
