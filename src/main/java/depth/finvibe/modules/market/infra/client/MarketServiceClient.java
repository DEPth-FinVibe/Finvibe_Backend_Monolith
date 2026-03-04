package depth.finvibe.modules.market.infra.client;

import depth.finvibe.modules.market.application.port.in.MarketQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarketServiceClient {
    private final MarketQueryUseCase marketQueryService;

    public Optional<Long> findStockIdBySymbol(String symbol) {
        return marketQueryService.findStockIdBySymbol(symbol);
    }

    public Optional<String> findSymbolByStockId(Long stockId) {
        return marketQueryService.findSymbolByStockId(stockId);
    }
}
