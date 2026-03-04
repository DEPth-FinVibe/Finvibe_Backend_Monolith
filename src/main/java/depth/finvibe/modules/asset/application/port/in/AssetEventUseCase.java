package depth.finvibe.modules.asset.application.port.in;

import depth.finvibe.modules.asset.application.event.AssetTransferredEvent;
import depth.finvibe.common.investment.dto.BatchPriceUpdatedEvent;
import depth.finvibe.common.investment.dto.SignUpEvent;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;

public interface AssetEventUseCase {
    void handleTradeExecutedEvent(TradeExecutedEvent event);

    void handleSignUpEvent(SignUpEvent event);

    void handleBatchPriceUpdatedEvent(BatchPriceUpdatedEvent event);

    void handleAssetTransferredEvent(AssetTransferredEvent event);
}
