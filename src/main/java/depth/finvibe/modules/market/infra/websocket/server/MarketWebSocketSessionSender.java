package depth.finvibe.modules.market.infra.websocket.server;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketWebSocketSessionSender {

	private final MarketWebSocketRegistry registry;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;
	@Qualifier("marketWsSendExecutor")
	private final Executor sendExecutor;

	@Value("${market.ws.fanout.max-pending-messages-per-session:32}")
	private int maxPendingMessagesPerSession;

	@Value("${market.ws.send-failure-threshold:3}")
	private int sendFailureThreshold;

	@Value("${market.ws.send-slow-threshold-ms:1000}")
	private long slowSendThresholdMs;

	@Value("${market.ws.send-slow-strikes-threshold:2}")
	private int slowSendStrikesThreshold;

	private Counter sendFailureCounter;
	private Counter closedByFailureCounter;
	private Counter slowConsumerEvictionCounter;
	private Counter fanoutRejectedCounter;
	private Counter droppedMessageCounter;
	private Counter coalescedMessageCounter;

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
		fanoutRejectedCounter = Counter.builder("ws.fanout.rejected")
				.description("fanout/send worker 제출 거부 수")
				.register(meterRegistry);
		droppedMessageCounter = Counter.builder("ws.message.dropped")
				.description("세션 pending queue 상한으로 드롭된 메시지 수")
				.register(meterRegistry);
		coalescedMessageCounter = Counter.builder("ws.message.coalesced")
				.description("토픽 최신값으로 덮어쓴 메시지 수")
				.register(meterRegistry);
	}

	public boolean enqueueQuote(MarketWebSocketConnection connection, TextMessage message, String topic) {
		return enqueue(connection, message, topic, true);
	}

	public boolean enqueueControl(MarketWebSocketConnection connection, Map<String, Object> payload) {
		TextMessage message = toTextMessage(payload);
		if (message == null) {
			return false;
		}
		return enqueue(connection, message, null, false);
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

	private boolean enqueue(
			MarketWebSocketConnection connection,
			TextMessage message,
			String coalescingKey,
			boolean evictOnDrop
	) {
		MarketWebSocketConnection.EnqueueResult result =
				connection.enqueueMessage(message, coalescingKey, maxPendingMessagesPerSession);
		switch (result) {
			case ENQUEUED -> {
				tryScheduleSendDrain(connection);
				return true;
			}
			case COALESCED -> {
				coalescedMessageCounter.increment();
				return true;
			}
			case DROPPED -> {
				droppedMessageCounter.increment();
				if (evictOnDrop) {
					evictSlowConsumer(connection, "pending_queue_overflow");
				}
				return false;
			}
		}
		return false;
	}

	private void tryScheduleSendDrain(MarketWebSocketConnection connection) {
		try {
			connection.scheduleDrainIfNeeded(sendExecutor, () -> drainConnection(connection));
		} catch (RejectedExecutionException ex) {
			fanoutRejectedCounter.increment();
			evictSlowConsumer(connection, "send_executor_rejected");
		}
	}

	private void drainConnection(MarketWebSocketConnection connection) {
		try {
			while (true) {
				MarketWebSocketConnection.PendingMessage pendingMessage = connection.pollPendingMessage();
				if (pendingMessage == null) {
					return;
				}
				if (!sendMessageSafely(connection, pendingMessage.currentMessage())) {
					return;
				}
			}
		} finally {
			connection.markDrainComplete();
			if (connection.hasPendingMessages()) {
				tryScheduleSendDrain(connection);
			}
		}
	}

	private boolean sendMessageSafely(MarketWebSocketConnection connection, TextMessage message) {
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
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
			if (elapsedMs >= slowSendThresholdMs) {
				int slowStrike = connection.incrementSlowSend();
				if (slowStrike >= slowSendStrikesThreshold) {
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
			if (isPartialWritingError(ex) || failureCount >= sendFailureThreshold) {
				closeAndRemoveSession(connection, "send_failure");
				return false;
			}
			log.debug("Failed to send websocket event - sessionId: {}, failureCount: {}, cause: {}",
					session.getId(), failureCount, ex.toString());
			return true;
		}
	}

	private boolean isPartialWritingError(Exception ex) {
		return ex instanceof IllegalStateException
				&& ex.getMessage() != null
				&& ex.getMessage().contains("TEXT_PARTIAL_WRITING");
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
			log.info("Closed websocket session - sessionId: {}, reason: {}, sendFailures: {}, pending: {}",
					sessionId, reason, connection.getTotalSendFailures(), connection.getPendingMessages());
		}
	}
}
