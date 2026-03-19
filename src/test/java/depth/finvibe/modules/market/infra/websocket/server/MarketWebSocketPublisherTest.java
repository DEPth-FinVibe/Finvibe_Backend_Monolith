package depth.finvibe.modules.market.infra.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class MarketWebSocketPublisherTest {

	@Test
	void publishCoalescesHotTopicBeforeFanoutRuns() throws Exception {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		MarketWebSocketRegistry registry = new MarketWebSocketRegistry(
				mock(CurrentPriceCommandUseCase.class),
				new ConcurrentTaskScheduler(),
				meterRegistry
		);
		registry.startTtlRefreshScheduler();

		ManualExecutor fanoutExecutor = new ManualExecutor();
		ManualExecutor sendExecutor = new ManualExecutor();
		MarketWebSocketSessionSender sessionSender = new MarketWebSocketSessionSender(
				registry,
				new ObjectMapper(),
				meterRegistry,
				sendExecutor
		);
		sessionSender.initMetrics();
		ReflectionTestUtils.setField(sessionSender, "maxPendingMessagesPerSession", 8);
		ReflectionTestUtils.setField(sessionSender, "sendFailureThreshold", 3);
		ReflectionTestUtils.setField(sessionSender, "slowSendThresholdMs", 1000L);
		ReflectionTestUtils.setField(sessionSender, "slowSendStrikesThreshold", 2);
		MarketWebSocketPublisher publisher = new MarketWebSocketPublisher(
				registry,
				new ObjectMapper(),
				meterRegistry,
				sessionSender,
				fanoutExecutor
		);
		publisher.initMetrics();
		ReflectionTestUtils.setField(publisher, "fanoutChunkSize", 100);

		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("s1");
		when(session.isOpen()).thenReturn(true);
		doNothing().when(session).sendMessage(any(TextMessage.class));

		MarketWebSocketConnection connection = registry.register(session);
		registry.authenticate(connection, UUID.randomUUID());
		registry.subscribe(connection, List.of("quote:1"), 30);

		publisher.publish(priceEvent(1L, 1000));
		publisher.publish(priceEvent(1L, 1001));

		assertThat(fanoutExecutor.size()).isEqualTo(1);
		fanoutExecutor.runAll();
		sendExecutor.runAll();

		ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
		verify(session, times(1)).sendMessage(messageCaptor.capture());
		TextMessage sentMessage = messageCaptor.getValue();
		assertThat(sentMessage.getPayload()).contains("\"price\":1001");
		assertThat(meterRegistry.find("ws.fanout.duration").timer().count()).isEqualTo(1);
		assertThat(meterRegistry.find("ws.fanout.chunk.duration").timer().count()).isEqualTo(1);
		registry.stopTtlRefreshScheduler();
	}

	@Test
	void publishEvictsSlowConsumerWhenPendingQueueOverflows() throws Exception {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		MarketWebSocketRegistry registry = new MarketWebSocketRegistry(
				mock(CurrentPriceCommandUseCase.class),
				new ConcurrentTaskScheduler(),
				meterRegistry
		);
		registry.startTtlRefreshScheduler();

		ManualExecutor fanoutExecutor = new ManualExecutor();
		ManualExecutor sendExecutor = new ManualExecutor();
		MarketWebSocketSessionSender sessionSender = new MarketWebSocketSessionSender(
				registry,
				new ObjectMapper(),
				meterRegistry,
				sendExecutor
		);
		sessionSender.initMetrics();
		ReflectionTestUtils.setField(sessionSender, "maxPendingMessagesPerSession", 1);
		ReflectionTestUtils.setField(sessionSender, "sendFailureThreshold", 2);
		ReflectionTestUtils.setField(sessionSender, "slowSendThresholdMs", 1000L);
		ReflectionTestUtils.setField(sessionSender, "slowSendStrikesThreshold", 2);
		MarketWebSocketPublisher publisher = new MarketWebSocketPublisher(
				registry,
				new ObjectMapper(),
				meterRegistry,
				sessionSender,
				fanoutExecutor
		);
		publisher.initMetrics();
		ReflectionTestUtils.setField(publisher, "fanoutChunkSize", 100);

		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("s-overflow");
		when(session.isOpen()).thenReturn(true);

		MarketWebSocketConnection connection = registry.register(session);
		registry.authenticate(connection, UUID.randomUUID());
		registry.subscribe(connection, List.of("quote:1", "quote:2"), 30);

		publisher.publish(priceEvent(1L, 1000));
		fanoutExecutor.runAll();
		assertThat(sendExecutor.size()).isEqualTo(1);

		publisher.publish(priceEvent(2L, 2000));
		fanoutExecutor.runAll();

		verify(session, times(1)).close(CloseStatus.SESSION_NOT_RELIABLE);
		assertThat(registry.getConnection("s-overflow")).isNull();
		assertThat(meterRegistry.find("ws.message.dropped").counter().count()).isEqualTo(1.0d);
		assertThat(meterRegistry.find("ws.connection.closed.slow-consumer").counter().count()).isEqualTo(1.0d);
		registry.stopTtlRefreshScheduler();
	}

	private CurrentPriceUpdatedEvent priceEvent(Long stockId, long close) {
		CurrentPriceUpdatedEvent event = new CurrentPriceUpdatedEvent();
		event.setStockId(stockId);
		event.setClose(BigDecimal.valueOf(close));
		event.setOpen(BigDecimal.valueOf(close - 10));
		event.setHigh(BigDecimal.valueOf(close + 10));
		event.setLow(BigDecimal.valueOf(close - 20));
		event.setPrevDayChangePct(BigDecimal.valueOf(1.23));
		event.setVolume(BigDecimal.valueOf(1000));
		event.setValue(BigDecimal.valueOf(5000));
		return event;
	}

	private static final class ManualExecutor implements java.util.concurrent.Executor {
		private final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable command) {
			tasks.offer(command);
		}

		int size() {
			return tasks.size();
		}

		void runAll() {
			while (!tasks.isEmpty()) {
				tasks.poll().run();
			}
		}
	}
}
