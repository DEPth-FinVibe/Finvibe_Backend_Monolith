package depth.finvibe.modules.market.infra.websocket.server;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class CustomWebSocketSession {
    private final String sessionId;
    private boolean authenticated;
    private String userId;
    private LocalDateTime lastAuthTime;
    private LocalDateTime lastPingTime;
    private int missedPongCount;
    private final Set<String> subscribedTopics;

    public CustomWebSocketSession(String sessionId) {
        this.sessionId = sessionId;
        this.authenticated = false;
        this.missedPongCount = 0;
        this.subscribedTopics = ConcurrentHashMap.newKeySet();
    }

    public void authenticate(String userId) {
        this.authenticated = true;
        this.userId = userId;
        this.lastAuthTime = LocalDateTime.now();
    }

    public void resetPongCount() {
        this.missedPongCount = 0;
        this.lastPingTime = LocalDateTime.now();
    }

    public void incrementMissedPong() {
        this.missedPongCount++;
    }

    public boolean shouldDisconnect() {
        return missedPongCount >= 3;
    }

    public boolean isAuthenticationExpired(int timeoutMinutes) {
        if (!authenticated || lastAuthTime == null) {
            return true;
        }
        return lastAuthTime.plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now());
    }
}