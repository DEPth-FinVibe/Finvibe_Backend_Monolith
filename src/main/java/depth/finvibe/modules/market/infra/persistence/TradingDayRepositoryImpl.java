package depth.finvibe.modules.market.infra.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.market.application.port.out.TradingDayRepository;
import depth.finvibe.modules.market.domain.TradingDay;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TradingDayRepositoryImpl implements TradingDayRepository {

  private final TradingDayJpaRepository jpaRepository;
  private final TradingDayQueryRepository queryRepository;

  @Override
  @Transactional
  public void saveAll(List<TradingDay> tradingDays) {
    jpaRepository.saveAll(tradingDays);
  }

  @Override
  public Optional<LocalDate> findLastOpenDayOnOrBefore(LocalDate date) {
    return queryRepository.findLastOpenDayOnOrBefore(date);
  }

  @Override
  public long countByYearMonth(int year, int month) {
    return queryRepository.countByYearMonth(year, month);
  }
}
