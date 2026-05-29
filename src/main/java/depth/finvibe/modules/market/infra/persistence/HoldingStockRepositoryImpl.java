package depth.finvibe.modules.market.infra.persistence;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;
import depth.finvibe.modules.market.domain.HoldingStock;

@RequiredArgsConstructor
public class HoldingStockRepositoryImpl implements HoldingStockRepository {
    private final HoldingStockJpaRepository jpaRepository;

    @Override
    @Transactional
    public void registerHoldingStock(Long stockId, Long userId) {
        jpaRepository.findByStockIdAndUserId(stockId, userId)
                .orElseGet(() -> jpaRepository.save(HoldingStock.create(stockId, userId)));
    }

    @Override
    @Transactional
    public void unregisterHoldingStock(Long stockId, Long userId) {
        jpaRepository.findByStockIdAndUserId(stockId, userId)
                .ifPresent(jpaRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findAllDistinctStockIds() {
        return jpaRepository.findAllDistinctStockIds();
    }
}
