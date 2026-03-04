package depth.finvibe.modules.trade.infra.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.in.MarketQueryUseCase;
import depth.finvibe.modules.market.application.port.in.MarketStatusQueryUseCase;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.dto.MarketStatusDto;
import depth.finvibe.modules.trade.application.port.out.MarketClient;

@Component
@RequiredArgsConstructor
public class MarketClientImpl implements MarketClient {
    private final MarketStatusQueryUseCase marketStatusQueryUseCase;
    private final MarketQueryUseCase marketQueryUseCase;

    @Override
    public boolean isMarketOpen() {
        MarketStatusDto.Response status = marketStatusQueryUseCase.getMarketStatus();
        return status.getStatus().equals(MarketStatus.OPEN);
    }

    @Override
    public Long getCurrentPrice(Long stockId) {
        return marketQueryUseCase.getStockPriceInternal(stockId);
    }

    @Override
    public String getStockNameById(Long stockId) {
        return marketQueryUseCase.getStockById(stockId).getName();
    }
}
