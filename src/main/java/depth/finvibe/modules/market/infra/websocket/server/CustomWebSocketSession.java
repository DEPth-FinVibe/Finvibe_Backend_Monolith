package depth.finvibe.modules.market.infra.websocket.server;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class CustomWebSocketSession {
    private final String sessionId;
    private final long connectedAtEpochMs;
    private boolean authenticated;
    private UUID userId;
    private long lastAuthEpochMs;
    private long lastPingEpochMs;
    private int missedPongCount;
    private final Set<Long> subscribedStockIds;

    public CustomWebSocketSession(String sessionId) {
        this.sessionId = sessionId;
        this.connectedAtEpochMs = System.currentTimeMillis();
        this.authenticated = false;
        this.lastAuthEpochMs = -1L;
        this.lastPingEpochMs = -1L;
        this.missedPongCount = 0;
        this.subscribedStockIds = ConcurrentHashMap.newKeySet();
    }

    public void authenticate(UUID userId) {
        this.authenticated = true;
        this.userId = userId;
        this.lastAuthEpochMs = System.currentTimeMillis();
    }

    public void resetPongCount() {
        this.missedPongCount = 0;
        this.lastPingEpochMs = System.currentTimeMillis();
    }

    public void incrementMissedPong() {
        this.missedPongCount++;
    }

    public boolean shouldDisconnect() {
        return missedPongCount >= 3;
    }

    public boolean isAuthenticationExpired(long timeoutMs) {
        if (authenticated) {
            return false;
        }
        return System.currentTimeMillis() - connectedAtEpochMs > timeoutMs;
    }
}
