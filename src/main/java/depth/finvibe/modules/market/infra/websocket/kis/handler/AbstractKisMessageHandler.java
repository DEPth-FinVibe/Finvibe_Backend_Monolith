package depth.finvibe.modules.market.infra.websocket.kis.handler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractKisMessageHandler implements KisMessageHandler {
    @Override
    public void handleError(Throwable t) {
        log.error("KIS WebSocket 에러 발생", t);
        throw new RuntimeException(t);
    }

    @Override
    public void handleDisconnect() {
        //Do nothing
    }

    @Override
    public void handleConnect() {
        //Do nothing
    }
}
