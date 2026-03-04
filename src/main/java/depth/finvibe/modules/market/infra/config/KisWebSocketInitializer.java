package depth.finvibe.modules.market.infra.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.infra.websocket.kis.KisConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisWebSocketInitializer {

    private final KisConnectionPool kisConnectionPool;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeKisWebSocketSessions() {
        log.info("KIS WebSocket 세션 초기화 이벤트 수신");
        kisConnectionPool.initializeSessions();
    }
}
