package depth.finvibe.modules.market.infra.websocket.server;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class MarketWebSocketSessionSender {

	private final MarketWebSocketRegistry registry;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	@Value("${market.ws.send.failure-threshold:3}")
	private int sendFailureThreshold;

	@Value("${market.ws.send.slow-threshold-ms:1000}")
	private long slowSendThresholdMs;

	@Value("${market.ws.send.slow-strikes-threshold:2}")
	private int slowSendStrikesThreshold;

	private Counter sendFailureCounter;
	private Counter closedByFailureCounter;
	private Counter slowConsumerEvictionCounter;
	private Counter directSendCounter;

	public MarketWebSocketSessionSender(
			MarketWebSocketRegistry registry,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry
	) {
		this.registry = registry;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
	}

	@PostConstruct
	public void initMetrics() {
		sendFailureCounter = Counter.builder("ws.message.send.failures")
				.description("WebSocket 메시지 전송 실패 수")
				.register(meterRegistry);
		closedByFailureCounter = Counter.builder("ws.connection.closed.unreliable")
				.description("반복 전송 실패로 강제 종료된 연결 수")
				.register(meterRegistry);
		slowConsumerEvictionCounter = Counter.builder("ws.connection.closed.slow-consumer")
				.description("느린 소비자로 강제 종료된 연결 수")
				.register(meterRegistry);
		directSendCounter = Counter.builder("ws.message.sent.direct")
				.description("직접 전송된 WebSocket 메시지 수")
				.register(meterRegistry);
	}

	public boolean sendQuote(MarketWebSocketConnection connection, TextMessage message) {
		return sendMessageSafely(connection, message, true);
	}

	public boolean sendControl(MarketWebSocketConnection connection, Map<String, Object> payload) {
		TextMessage message = toTextMessage(payload);
		if (message == null) {
			return false;
		}
		return sendMessageSafely(connection, message, false);
	}

	public boolean sendImmediate(WebSocketSession session, Map<String, Object> payload) {
		TextMessage message = toTextMessage(payload);
		if (message == null) {
			return false;
		}
		try {
			synchronized (session) {
				if (!session.isOpen()) {
					return false;
				}
				session.sendMessage(message);
				directSendCounter.increment();
				return true;
			}
		} catch (Exception ex) {
			log.warn("Failed to send websocket message immediately - sessionId: {}", session.getId(), ex);
			return false;
		}
	}

	private TextMessage toTextMessage(Map<String, Object> payload) {
		try {
			return new TextMessage(objectMapper.writeValueAsString(payload));
		} catch (Exception ex) {
			log.warn("Failed to serialize websocket control message.", ex);
			return null;
		}
	}

	private boolean sendMessageSafely(MarketWebSocketConnection connection, TextMessage message, boolean evictOnSlow) {
		WebSocketSession session = connection.getSession();
		long startNs = System.nanoTime();
		try {
			synchronized (session) {
				if (!session.isOpen()) {
					registry.remove(session.getId());
					return false;
				}
				session.sendMessage(message);
			}
			directSendCounter.increment();
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
			if (elapsedMs >= slowSendThresholdMs) {
				int slowStrike = connection.incrementSlowSend();
				if (evictOnSlow && slowStrike >= slowSendStrikesThreshold) {
					evictSlowConsumer(connection, "send_timeout");
					return false;
				}
			} else {
				connection.recordSendSuccess();
			}
			return true;
		} catch (Exception ex) {
			sendFailureCounter.increment();
			int failureCount = connection.incrementSendFailure();
			registry.updateConnectionGauges();
			if (isUnrecoverableSendError(ex) || failureCount >= sendFailureThreshold) {
				closeAndRemoveSession(connection, "send_failure");
				return false;
			}
			log.debug("Failed to send websocket event - sessionId: {}, failureCount: {}, cause: {}",
					session.getId(), failureCount, ex.toString());
			return true;
		}
	}

	private boolean isUnrecoverableSendError(Exception ex) {
		if (ex instanceof IllegalStateException && ex.getMessage() != null) {
			return ex.getMessage().contains("TEXT_PARTIAL_WRITING")
					|| ex.getMessage().contains("buffer")
					|| ex.getMessage().contains("send time limit");
		}
		return false;
	}

	private void evictSlowConsumer(MarketWebSocketConnection connection, String reason) {
		connection.recordSlowEviction();
		slowConsumerEvictionCounter.increment();
		closeAndRemoveSession(connection, reason);
	}

	private void closeAndRemoveSession(MarketWebSocketConnection connection, String reason) {
		String sessionId = connection.getSession().getId();
		try {
			if (connection.getSession().isOpen()) {
				connection.getSession().close(CloseStatus.SESSION_NOT_RELIABLE);
			}
		} catch (Exception ex) {
			log.warn("Failed to close unreliable websocket session {}.", sessionId, ex);
		} finally {
			closedByFailureCounter.increment();
			registry.remove(sessionId);
			log.info("Closed websocket session - sessionId: {}, reason: {}, sendFailures: {}",
					sessionId, reason, connection.getTotalSendFailures());
		}
	}
}
