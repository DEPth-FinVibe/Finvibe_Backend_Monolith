package depth.finvibe.modules.market.application;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.application.port.in.MarketEventUseCase;
import depth.finvibe.common.investment.dto.StockHoldingChangedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketEventService implements MarketEventUseCase {

    private final CurrentPriceCommandUseCase currentPriceCommandUseCase;


    public void handleStockHoldingChangedEvent(StockHoldingChangedEvent event) {
        Long stockId = event.getStockId();
        UUID userId = event.getUserId();
        Boolean isHolding = event.getIsHolding();

        //TODO: 보유종목 갱신

        if(isHolding) {
            currentPriceCommandUseCase.registerHoldingStock(stockId, userId);
        }else{
            currentPriceCommandUseCase.unregisterHoldingStock(stockId, userId);
        }

        log.info("Handled StockHoldingChangedEvent: stockId={}, userId={}, isHolding={}", stockId, userId, isHolding);
    }
}
