package depth.finvibe.modules.market.infra.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.market.domain.ClosingPrice;

public interface ClosingPriceJpaRepository extends JpaRepository<ClosingPrice, Long> {
  List<ClosingPrice> findByStockIdInAndTradingDate(List<Long> stockIds, LocalDate tradingDate);
}
