package depth.finvibe.modules.market.infra.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.market.application.port.out.ClosingPriceRepository;
import depth.finvibe.modules.market.domain.ClosingPrice;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ClosingPriceRepositoryImpl implements ClosingPriceRepository {

  private final ClosingPriceJpaRepository jpaRepository;

  @Override
  public List<ClosingPrice> findByStockIdsAndTradingDate(List<Long> stockIds, LocalDate tradingDate) {
    if (stockIds == null || stockIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository.findByStockIdInAndTradingDate(stockIds, tradingDate);
  }

  @Override
  @Transactional
  public void saveAll(List<ClosingPrice> closingPrices) {
    if (closingPrices == null || closingPrices.isEmpty()) {
      return;
    }
    jpaRepository.saveAll(closingPrices);
  }

  @Override
  @Transactional
  public void deleteAll() {
    jpaRepository.deleteAllInBatch();
  }
}
