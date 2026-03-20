package depth.finvibe.modules.market.infra.websocket.server;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MarketWebSocketPublisher {
	private static final String EXCHANGE = "KRX";
	private static final Logger log = LoggerFactory.getLogger(MarketWebSocketPublisher.class);

	private final MarketWebSocketRegistry registry;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;
	private final MarketWebSocketSessionSender sessionSender;

	private io.micrometer.core.instrument.Counter serializationErrorCounter;
	private io.micrometer.core.instrument.Counter eventsDispatchedCounter;

	@PostConstruct
	public void initMetrics() {
		serializationErrorCounter = io.micrometer.core.instrument.Counter.builder("ws.message.serialization.errors")
				.description("WebSocket 이벤트 직렬화 실패 수")
				.register(meterRegistry);
		eventsDispatchedCounter = io.micrometer.core.instrument.Counter.builder("ws.events.dispatched")
				.description("클라이언트에게 enqueue된 이벤트 총 건수")
				.register(meterRegistry);
	}

	public void publish(CurrentPriceUpdatedEvent event) {
		if (event == null) {
			return;
		}
		Map<String, Object> eventData = objectMapper.convertValue(event, Map.class);
		Long stockId = toLong(eventData.get("stockId"));
		if (stockId == null) {
			return;
		}
		String topic = "quote:" + stockId;
		TextMessage payload = serializeEvent(topic, stockId, eventData);
		if (payload == null) {
			return;
		}
		fanout(topic, payload);
	}

	private TextMessage serializeEvent(String topic, Long stockId, Map<String, Object> eventData) {
		try {
			return new TextMessage(objectMapper.writeValueAsString(
					new QuoteEventPayload(
							"event",
							topic,
							Instant.now().toEpochMilli(),
							new QuoteEventData(
									stockId,
									EXCHANGE,
									eventData.get("close"),
									eventData.get("open"),
									eventData.get("high"),
									eventData.get("low"),
									eventData.get("prevDayChangePct"),
									eventData.get("volume"),
									eventData.get("value")
							)
					)
			));
		} catch (Exception ex) {
			serializationErrorCounter.increment();
			log.warn("Failed to serialize websocket event.", ex);
			return null;
		}
	}

	private Long toLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return null;
	}

	private void fanout(String topic, TextMessage message) {
		String[] subscriberIds = registry.snapshotSubscriberIds(topic);
      for (String subscriberId : subscriberIds) {
          MarketWebSocketConnection connection = registry.getConnection(subscriberId);
          if (connection == null) {
              continue;
          }
          if (sessionSender.sendQuote(connection, message)) {
              eventsDispatchedCounter.increment();
          }
      }
	}

	private record QuoteEventPayload(
			String type,
			String topic,
			long ts,
			QuoteEventData data
	) {
	}

	private record QuoteEventData(
			Long stockId,
			String exchange,
			Object price,
			Object open,
			Object high,
			Object low,
			Object prevDayChangePct,
			Object volume,
			Object value
	) {
	}
}
