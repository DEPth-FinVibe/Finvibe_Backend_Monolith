package depth.finvibe.modules.market.infra.websocket.server;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import org.springframework.web.socket.WebSocketSession;

public class MarketWebSocketConnection {
    @Getter
    private final WebSocketSession session;

    @Getter
    private final CustomWebSocketSession state;

    private long rateWindowSecond = -1;
    private int rateCount = 0;
    private final AtomicInteger consecutiveSendFailures = new AtomicInteger(0);
    private final AtomicInteger totalSendFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSlowSends = new AtomicInteger(0);
    private final AtomicInteger slowEvictionCount = new AtomicInteger(0);

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

    public java.util.UUID getUserId() {
        return state.getUserId();
    }

    public void recordSendSuccess() {
        consecutiveSendFailures.set(0);
        consecutiveSlowSends.set(0);
    }

    public int incrementSendFailure() {
        totalSendFailures.incrementAndGet();
        return consecutiveSendFailures.incrementAndGet();
    }

    public int incrementSlowSend() {
        return consecutiveSlowSends.incrementAndGet();
    }

    public int getTotalSendFailures() {
        return totalSendFailures.get();
    }

    public int getSlowEvictionCount() {
        return slowEvictionCount.get();
    }

    public void recordSlowEviction() {
        slowEvictionCount.incrementAndGet();
    }
}
