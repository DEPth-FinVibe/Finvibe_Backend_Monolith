package depth.finvibe.modules.market.infra.websocket.server;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class MarketWebSocketConnection {
    public enum EnqueueResult {
        ENQUEUED,
        COALESCED,
        DROPPED
    }

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
    private final AtomicInteger totalSendFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSlowSends = new AtomicInteger(0);
    private final AtomicInteger slowEvictionCount = new AtomicInteger(0);
    private final AtomicInteger pendingMessages = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<PendingMessage> outboundQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, PendingMessage> topicPendingMessages = new ConcurrentHashMap<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

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

    public int getPendingMessages() {
        return pendingMessages.get();
    }

    public boolean hasPendingMessages() {
        return pendingMessages.get() > 0;
    }

    public PendingMessage pollPendingMessage() {
        PendingMessage pendingMessage = outboundQueue.poll();
        if (pendingMessage == null) {
            return null;
        }
        topicPendingMessages.remove(pendingMessage.topic(), pendingMessage);
        pendingMessages.decrementAndGet();
        return pendingMessage;
    }

    public void markDrainComplete() {
        drainScheduled.set(false);
    }

    public boolean scheduleDrainIfNeeded(Executor executor, Runnable drainTask) {
        if (!hasPendingMessages()) {
            return false;
        }
        if (!drainScheduled.compareAndSet(false, true)) {
            return false;
        }
        try {
            executor.execute(drainTask);
            return true;
        } catch (RuntimeException ex) {
            drainScheduled.set(false);
            throw ex;
        }
    }

    public EnqueueResult enqueueMessage(TextMessage message, String coalescingKey, int maxPendingMessages) {
        if (coalescingKey != null) {
            PendingMessage pendingMessage = topicPendingMessages.get(coalescingKey);
            if (pendingMessage != null) {
                pendingMessage.replaceMessage(message);
                return EnqueueResult.COALESCED;
            }
        }

        while (true) {
            int currentPending = pendingMessages.get();
            if (currentPending >= maxPendingMessages) {
                return EnqueueResult.DROPPED;
            }
            if (pendingMessages.compareAndSet(currentPending, currentPending + 1)) {
                PendingMessage newPendingMessage = new PendingMessage(coalescingKey, message);
                if (coalescingKey != null) {
                    PendingMessage previous = topicPendingMessages.putIfAbsent(coalescingKey, newPendingMessage);
                    if (previous != null) {
                        pendingMessages.decrementAndGet();
                        previous.replaceMessage(message);
                        return EnqueueResult.COALESCED;
                    }
                }
                outboundQueue.offer(newPendingMessage);
                return EnqueueResult.ENQUEUED;
            }
        }
    }

    public record PendingMessage(String topic, java.util.concurrent.atomic.AtomicReference<TextMessage> messageRef) {
        public PendingMessage(String topic, TextMessage message) {
            this(topic, new java.util.concurrent.atomic.AtomicReference<>(message));
        }

        public TextMessage currentMessage() {
            return messageRef.get();
        }

        public void replaceMessage(TextMessage message) {
            messageRef.set(message);
        }
    }
}
