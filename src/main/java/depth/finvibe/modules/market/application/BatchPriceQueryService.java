package depth.finvibe.modules.market.application;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.in.BatchPriceQueryUseCase;
import depth.finvibe.modules.market.application.port.out.BatchUpdatePriceRepository;
import depth.finvibe.modules.market.domain.BatchUpdatePrice;
import depth.finvibe.common.investment.dto.BatchPriceSnapshot;

@Service
@RequiredArgsConstructor
public class BatchPriceQueryService implements BatchPriceQueryUseCase {
    private final BatchUpdatePriceRepository batchUpdatePriceRepository;

    @Override
    public List<BatchPriceSnapshot> getBatchPrices(List<Long> stockIds) {
        return batchUpdatePriceRepository.findByStockIds(stockIds).stream()
                .map(this::toSnapshot)
                .toList();
    }

    private BatchPriceSnapshot toSnapshot(BatchUpdatePrice price) {
        return BatchPriceSnapshot.builder()
                .stockId(price.getStockId())
                .price(price.getPrice())
                .at(price.getAt())
                .build();
    }
}
