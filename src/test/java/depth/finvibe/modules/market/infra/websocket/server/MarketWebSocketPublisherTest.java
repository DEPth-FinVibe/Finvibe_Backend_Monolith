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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
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

		ManualExecutorService fanoutExecutor = new ManualExecutorService();
		ManualExecutorService chunkExecutor = new ManualExecutorService();
		MarketWebSocketSessionSender sessionSender = new MarketWebSocketSessionSender(
				registry,
				new ObjectMapper(),
				meterRegistry
		);
		sessionSender.initMetrics();
		ReflectionTestUtils.setField(sessionSender, "sendFailureThreshold", 3);
		ReflectionTestUtils.setField(sessionSender, "slowSendThresholdMs", 1000L);
		ReflectionTestUtils.setField(sessionSender, "slowSendStrikesThreshold", 2);
		MarketWebSocketPublisher publisher = new MarketWebSocketPublisher(
				registry,
				new ObjectMapper(),
				meterRegistry,
				sessionSender,
				fanoutExecutor,
				chunkExecutor
		);
		publisher.initMetrics();
		ReflectionTestUtils.setField(publisher, "fanoutChunkSize", 100);
		ReflectionTestUtils.setField(publisher, "maxChunkParallelism", 2);

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
		chunkExecutor.runAll();

		ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
		verify(session, times(1)).sendMessage(messageCaptor.capture());
		assertThat(messageCaptor.getValue().getPayload()).contains("\"price\":1001");
		assertThat(meterRegistry.find("ws.fanout.duration").timer().count()).isEqualTo(1);
		assertThat(meterRegistry.find("ws.fanout.chunk.duration").timer().count()).isEqualTo(1);
		assertThat(meterRegistry.find("ws.message.coalesced").counter().count()).isEqualTo(1.0d);
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

	private static final class ManualExecutorService extends AbstractExecutorService {
		private final Queue<Runnable> tasks = new ArrayDeque<>();
		private boolean shutdown;

		@Override
		public void shutdown() {
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow() {
			shutdown = true;
			return List.copyOf(tasks);
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return shutdown && tasks.isEmpty();
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return true;
		}

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
