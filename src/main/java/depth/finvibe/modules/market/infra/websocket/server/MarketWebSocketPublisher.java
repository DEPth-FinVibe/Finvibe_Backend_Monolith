package depth.finvibe.modules.market.infra.websocket.server;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketWebSocketPublisher {
    private static final String EXCHANGE = "KRX";

    private final MarketWebSocketRegistry registry;
    private final ObjectMapper objectMapper;

    @Value("${market.ws.send-failure-threshold:3}")
    private int sendFailureThreshold;

    public void publish(CurrentPriceUpdatedEvent event) {
        if (event == null || event.getStockId() == null) {
            return;
        }
        String topic = "quote:" + event.getStockId();
        Map<String, Object> payload = buildEventPayload(event, topic);
        String message;
        try {
            message = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("Failed to serialize websocket event.", ex);
            return;
        }

        TextMessage textMessage = new TextMessage(message);
        for (MarketWebSocketConnection connection : registry.getSubscribers(topic)) {
            try {
                WebSocketSession session = connection.getSession();
                if (sendMessageSafely(session, textMessage)) {
                    connection.recordSendSuccess();
                } else {
                    registry.remove(session.getId());
                }
            } catch (Exception ex) {
                int failureCount = connection.incrementSendFailure();
                if (isPartialWritingError(ex)) {
                    log.trace("Skipped websocket send while previous message is still writing - sessionId: {}, failureCount: {}",
                            connection.getSession().getId(), failureCount);
                } else {
                    if (failureCount >= sendFailureThreshold) {
                        log.warn("Failed to send websocket event repeatedly - sessionId: {}, failureCount: {}, threshold: {}, cause: {}",
                                connection.getSession().getId(), failureCount, sendFailureThreshold, ex.toString());
                    } else {
                        log.debug("Failed to send websocket event (will retry) - sessionId: {}, failureCount: {}, threshold: {}, cause: {}",
                                connection.getSession().getId(), failureCount, sendFailureThreshold, ex.toString());
                    }
                }

                if (failureCount >= sendFailureThreshold) {
                    closeAndRemoveSession(connection);
                }
            }
        }
    }

    private boolean sendMessageSafely(WebSocketSession session, TextMessage message) throws Exception {
        synchronized (session) {
            if (!session.isOpen()) {
                return false;
            }
            session.sendMessage(message);
            return true;
        }
    }

    private boolean isPartialWritingError(Exception ex) {
        return ex instanceof IllegalStateException
                && ex.getMessage() != null
                && ex.getMessage().contains("TEXT_PARTIAL_WRITING");
    }

    private void closeAndRemoveSession(MarketWebSocketConnection connection) {
        String sessionId = connection.getSession().getId();
        try {
            if (connection.getSession().isOpen()) {
                connection.getSession().close(CloseStatus.SESSION_NOT_RELIABLE);
            }
        } catch (Exception ex) {
            log.warn("Failed to close unreliable websocket session {}.", sessionId, ex);
        } finally {
            registry.remove(sessionId);
            log.info("Closed websocket session due to repeated send failures - sessionId: {}, threshold: {}",
                    sessionId, sendFailureThreshold);
        }
    }

    private Map<String, Object> buildEventPayload(CurrentPriceUpdatedEvent event, String topic) {
        Map<String, Object> data = new HashMap<>();
        data.put("stockId", event.getStockId());
        data.put("exchange", EXCHANGE);
        data.put("price", event.getClose());
        data.put("open", event.getOpen());
        data.put("high", event.getHigh());
        data.put("low", event.getLow());
        data.put("prevDayChangePct", event.getPrevDayChangePct());
        data.put("volume", event.getVolume());
        data.put("value", event.getValue());

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "event");
        payload.put("topic", topic);
        payload.put("ts", Instant.now().toEpochMilli());
        payload.put("data", data);
        return payload;
    }
}
