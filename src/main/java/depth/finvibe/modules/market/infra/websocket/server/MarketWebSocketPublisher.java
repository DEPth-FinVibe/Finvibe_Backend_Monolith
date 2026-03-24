package depth.finvibe.modules.market.infra.websocket.server;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.ObjectMapper;

@Component
public class MarketWebSocketPublisher {
	private static final String EXCHANGE = "KRX";
	private static final Logger log = LoggerFactory.getLogger(MarketWebSocketPublisher.class);

	private final MarketWebSocketRegistry registry;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;
	private final MarketWebSocketSessionSender sessionSender;
	private final ExecutorService fanoutExecutor;

	@Value("${market.ws.fanout.chunk-size:100}")
	private int fanoutChunkSize;

	@Value("${market.ws.fanout.max-chunk-parallelism:8}")
	private int maxChunkParallelism;

	public MarketWebSocketPublisher(
			MarketWebSocketRegistry registry,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry,
			MarketWebSocketSessionSender sessionSender,
			@Qualifier("marketWsFanoutExecutor") ExecutorService fanoutExecutor
	) {
		this.registry = registry;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
		this.sessionSender = sessionSender;
		this.fanoutExecutor = fanoutExecutor;
	}

	private io.micrometer.core.instrument.Counter serializationErrorCounter;
	private io.micrometer.core.instrument.Counter eventsDispatchedCounter;
	private io.micrometer.core.instrument.DistributionSummary fanoutRecipientsSummary;
	private Timer fanoutDurationTimer;

	@PostConstruct
	public void initMetrics() {
		serializationErrorCounter = io.micrometer.core.instrument.Counter.builder("ws.message.serialization.errors")
				.description("WebSocket 이벤트 직렬화 실패 수")
				.register(meterRegistry);
		eventsDispatchedCounter = io.micrometer.core.instrument.Counter.builder("ws.events.dispatched")
				.description("클라이언트에게 enqueue된 이벤트 총 건수")
				.register(meterRegistry);
		fanoutRecipientsSummary = io.micrometer.core.instrument.DistributionSummary.builder("ws.fanout.recipients")
				.description("이벤트 1건당 fanout 수신자 수")
				.baseUnit("recipients")
				.publishPercentileHistogram()
				.serviceLevelObjectives(1, 5, 10, 25, 50, 100, 250, 500, 1_000, 2_000, 5_000)
				.register(meterRegistry);
		fanoutDurationTimer = Timer.builder("ws.fanout.duration")
				.description("이벤트 1건의 fanout 완료 시간")
				.publishPercentileHistogram()
				.serviceLevelObjectives(
						Duration.ofMillis(1),
						Duration.ofMillis(5),
						Duration.ofMillis(10),
						Duration.ofMillis(25),
						Duration.ofMillis(50),
						Duration.ofMillis(100),
						Duration.ofMillis(250),
						Duration.ofMillis(500),
						Duration.ofSeconds(1),
						Duration.ofSeconds(2)
				)
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
		if (subscriberIds.length == 0) {
			return;
		}

		fanoutRecipientsSummary.record(subscriberIds.length);
		Timer.Sample sample = Timer.start(meterRegistry);

		int chunkSize = Math.max(1, fanoutChunkSize);
		int chunkCount = (int) Math.ceil((double) subscriberIds.length / chunkSize);
		int parallelChunks = Math.max(1, Math.min(maxChunkParallelism, chunkCount));

		try {
			if (chunkCount == 1 || parallelChunks == 1) {
				eventsDispatchedCounter.increment(sendChunk(subscriberIds, 0, subscriberIds.length, message));
				return;
			}

			List<Future<Integer>> activeFutures = new ArrayList<>(parallelChunks);
			int dispatchedCount = 0;

			for (int start = 0; start < subscriberIds.length; start += chunkSize) {
				int end = Math.min(start + chunkSize, subscriberIds.length);
				activeFutures.add(fanoutExecutor.submit(chunkTask(subscriberIds, start, end, message)));

				if (activeFutures.size() >= parallelChunks) {
					dispatchedCount += drainFirstFuture(activeFutures);
				}
			}

			for (Future<Integer> future : activeFutures) {
				dispatchedCount += awaitChunk(future);
			}

			if (dispatchedCount > 0) {
				eventsDispatchedCounter.increment(dispatchedCount);
			}
		} finally {
			sample.stop(fanoutDurationTimer);
		}
	}

	private Callable<Integer> chunkTask(String[] subscriberIds, int startInclusive, int endExclusive, TextMessage message) {
		return () -> sendChunk(subscriberIds, startInclusive, endExclusive, message);
	}

	private int sendChunk(String[] subscriberIds, int startInclusive, int endExclusive, TextMessage message) {
		int dispatchedCount = 0;
		for (int i = startInclusive; i < endExclusive; i++) {
			MarketWebSocketConnection connection = registry.getConnection(subscriberIds[i]);
			if (connection == null) {
				continue;
			}
			if (sessionSender.sendQuote(connection, message)) {
				dispatchedCount++;
			}
		}
		return dispatchedCount;
	}

	private int drainFirstFuture(List<Future<Integer>> activeFutures) {
		return awaitChunk(activeFutures.removeFirst());
	}

	private int awaitChunk(Future<Integer> future) {
		try {
			return future.get();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("WebSocket fanout interrupted.");
			return 0;
		} catch (ExecutionException ex) {
			log.warn("WebSocket fanout chunk failed.", ex.getCause());
			return 0;
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
