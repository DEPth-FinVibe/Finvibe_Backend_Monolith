package depth.finvibe.modules.asset.infra.client;

import depth.finvibe.modules.asset.application.port.out.MarketPriceClient;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.in.BatchPriceQueryUseCase;
import depth.finvibe.common.investment.dto.BatchPriceSnapshot;

@Component
@RequiredArgsConstructor
public class MarketInternalClient implements MarketPriceClient {
    private final BatchPriceQueryUseCase batchPriceQueryUseCase;

    @Override
    public List<BatchPriceSnapshot> getBatchPrices(List<Long> stockIds) {
        // 향후 마켓 서비스를 분리하면 REST 호출로 변경
        return batchPriceQueryUseCase.getBatchPrices(stockIds);
    }
}
