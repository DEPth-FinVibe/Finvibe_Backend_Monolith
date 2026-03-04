package depth.finvibe.modules.market.infra.websocket.server;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

public class MarketWebSocketConnection {
    @Getter
    private final WebSocketSession session;

    @Getter
    private final CustomWebSocketSession state;

    @Setter
    @Getter
    private ScheduledFuture<?> authTimeoutTask;

    @Setter
    @Getter
    private ScheduledFuture<?> heartbeatTask;

    private long rateWindowSecond = -1;
    private int rateCount = 0;
    private final AtomicInteger consecutiveSendFailures = new AtomicInteger(0);

    public MarketWebSocketConnection(WebSocketSession session) {
        this.session = session;
        this.state = new CustomWebSocketSession(session.getId());
    }

    public boolean tryConsume(int limitPerSecond) {
        long nowSecond = Instant.now().getEpochSecond();
        if (rateWindowSecond != nowSecond) {
            rateWindowSecond = nowSecond;
            rateCount = 0;
        }
        if (rateCount >= limitPerSecond) {
            return false;
        }
        rateCount++;
        return true;
    }

    public UUID getUserId() {
        if (state.getUserId() == null) {
            return null;
        }
        return UUID.fromString(state.getUserId());
    }

    public void recordSendSuccess() {
        consecutiveSendFailures.set(0);
    }

    public int incrementSendFailure() {
        return consecutiveSendFailures.incrementAndGet();
    }
}
