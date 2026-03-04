package depth.finvibe.modules.market.infra.websocket.kis.handler;

import depth.finvibe.modules.market.infra.websocket.kis.model.KisMessage;

public interface KisMessageHandler {
    void handleResponse(KisMessage.TransactionResponse response);

    void handleError(Throwable t);

    void handleDisconnect();

    void handleConnect();
}
