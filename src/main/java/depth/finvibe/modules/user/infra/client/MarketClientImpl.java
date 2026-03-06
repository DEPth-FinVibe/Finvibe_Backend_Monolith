package depth.finvibe.modules.user.infra.client;

import depth.finvibe.modules.market.application.port.in.MarketQueryUseCase;
import depth.finvibe.modules.user.application.port.out.MarketClient;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketClientImpl implements MarketClient {

    private static final Logger log = LoggerFactory.getLogger(MarketClientImpl.class);

    private final MarketQueryUseCase marketQueryUseCase;

    @Override
    public Optional<String> getStockNameByStockId(Long stockId) {
        try {
            return Optional.ofNullable(marketQueryUseCase.getStockNameById(stockId));
        } catch (Exception exception) {
            log.error("Failed to fetch stock name for stockId {}: {}", stockId, exception.getMessage());
            return Optional.empty();
        }
    }
}
