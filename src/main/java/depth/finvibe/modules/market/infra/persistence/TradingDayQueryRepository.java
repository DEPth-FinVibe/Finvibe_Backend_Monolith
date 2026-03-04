package depth.finvibe.modules.market.infra.persistence;

import java.time.LocalDate;
import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static depth.finvibe.modules.market.domain.QTradingDay.tradingDay;

@Repository
@RequiredArgsConstructor
public class TradingDayQueryRepository {

  private final JPAQueryFactory queryFactory;

  /**
   * 해당 일 이하 중 가장 최근 개장일 한 건 반환.
   */
  public Optional<LocalDate> findLastOpenDayOnOrBefore(LocalDate date) {
    LocalDate result = queryFactory
        .select(tradingDay.date)
        .from(tradingDay)
        .where(
            tradingDay.date.loe(date),
            tradingDay.openDay.isTrue()
        )
        .orderBy(tradingDay.date.desc())
        .fetchFirst();
    return Optional.ofNullable(result);
  }

  /**
   * 해당 연월에 저장된 거래일 레코드 수.
   */
  public long countByYearMonth(int year, int month) {
    Long count = queryFactory
        .select(tradingDay.date.count())
        .from(tradingDay)
        .where(
            tradingDay.date.year().eq(year),
            tradingDay.date.month().eq(month)
        )
        .fetchOne();
    return count != null ? count : 0L;
  }
}
