package depth.finvibe.modules.market.infra.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class MarketWebSocketPublisherTest {

	@Test
	void publishFansOutEventsToTopicSubscribers() throws Exception {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		MarketWebSocketRegistry registry = new MarketWebSocketRegistry(
				mock(CurrentPriceCommandUseCase.class),
				new ConcurrentTaskScheduler(),
				meterRegistry
		);
		registry.startTtlRefreshScheduler();

		MarketWebSocketSessionSender sessionSender = new MarketWebSocketSessionSender(
				registry,
				new ObjectMapper(),
				meterRegistry,
				Executors.newSingleThreadExecutor()
		);
		sessionSender.initMetrics();
		ReflectionTestUtils.setField(sessionSender, "sendFailureThreshold", 3);
		ReflectionTestUtils.setField(sessionSender, "slowSendThresholdMs", 1000L);
		ReflectionTestUtils.setField(sessionSender, "slowSendStrikesThreshold", 2);
		ExecutorService fanoutExecutor = Executors.newSingleThreadExecutor();
		MarketWebSocketPublisher publisher = new MarketWebSocketPublisher(
				registry,
				new ObjectMapper(),
				meterRegistry,
				sessionSender,
				fanoutExecutor
		);
		publisher.initMetrics();

		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("s1");
		when(session.isOpen()).thenReturn(true);
		doNothing().when(session).sendMessage(any(TextMessage.class));

		MarketWebSocketConnection connection = registry.register(session);
		registry.authenticate(connection, UUID.randomUUID());
		registry.subscribe(connection, List.of("quote:1"), 30);

		publisher.publish(priceEvent(1L, 1000));
		publisher.publish(priceEvent(1L, 1001));

		ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
		verify(session, timeout(1000).times(2)).sendMessage(messageCaptor.capture());
		assertThat(messageCaptor.getAllValues().get(0).getPayload()).contains("\"price\":1000");
		assertThat(messageCaptor.getAllValues().get(1).getPayload()).contains("\"price\":1001");
		assertThat(meterRegistry.find("ws.events.dispatched").counter().count()).isEqualTo(2.0d);
		fanoutExecutor.shutdown();
		registry.stopTtlRefreshScheduler();
	}

	private CurrentPriceUpdatedEvent priceEvent(Long stockId, long close) {
		CurrentPriceUpdatedEvent event = new CurrentPriceUpdatedEvent();
		ReflectionTestUtils.setField(event, "stockId", stockId);
		ReflectionTestUtils.setField(event, "close", BigDecimal.valueOf(close));
		ReflectionTestUtils.setField(event, "open", BigDecimal.valueOf(close - 10));
		ReflectionTestUtils.setField(event, "high", BigDecimal.valueOf(close + 10));
		ReflectionTestUtils.setField(event, "low", BigDecimal.valueOf(close - 20));
		ReflectionTestUtils.setField(event, "prevDayChangePct", BigDecimal.valueOf(1.23));
		ReflectionTestUtils.setField(event, "volume", BigDecimal.valueOf(1000));
		ReflectionTestUtils.setField(event, "value", BigDecimal.valueOf(5000));
		return event;
	}

}
