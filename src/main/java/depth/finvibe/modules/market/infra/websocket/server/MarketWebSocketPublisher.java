package depth.finvibe.modules.market.infra.websocket.server;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class MarketWebSocketPublisher {
	private static final String EXCHANGE = "KRX";

	private final MarketWebSocketRegistry registry;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;
	private final MarketWebSocketSessionSender sessionSender;
	private final Executor fanoutExecutor;
	private final ExecutorService chunkExecutor;

	public MarketWebSocketPublisher(
			MarketWebSocketRegistry registry,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry,
			MarketWebSocketSessionSender sessionSender,
			@Qualifier("marketWsFanoutExecutor") Executor fanoutExecutor,
			@Qualifier("marketWsSendExecutor") ExecutorService chunkExecutor
	) {
		this.registry = registry;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
		this.sessionSender = sessionSender;
		this.fanoutExecutor = fanoutExecutor;
		this.chunkExecutor = chunkExecutor;
	}

	private final Map<String, TopicFanoutState> topicFanoutStates = new ConcurrentHashMap<>();
	@Value("${market.ws.fanout.chunk-size:200}")
	private int fanoutChunkSize = 200;
	@Value("${market.ws.fanout.max-chunk-parallelism:4}")
	private int maxChunkParallelism;
	private io.micrometer.core.instrument.Counter serializationErrorCounter;
	private io.micrometer.core.instrument.Counter eventsDispatchedCounter;
	private io.micrometer.core.instrument.Counter fanoutRejectedCounter;
	private io.micrometer.core.instrument.Counter coalescedMessageCounter;
	private DistributionSummary fanoutRecipientsHistogram;
	private Timer fanoutDurationTimer;
	private Timer fanoutChunkDurationTimer;

	@PostConstruct
	public void initMetrics() {
		serializationErrorCounter = io.micrometer.core.instrument.Counter.builder("ws.message.serialization.errors")
				.description("WebSocket 이벤트 직렬화 실패 수")
				.register(meterRegistry);
		eventsDispatchedCounter = io.micrometer.core.instrument.Counter.builder("ws.events.dispatched")
				.description("클라이언트에게 enqueue된 이벤트 총 건수")
				.register(meterRegistry);
		fanoutRejectedCounter = io.micrometer.core.instrument.Counter.builder("ws.fanout.rejected")
				.description("fanout worker 제출 거부 수")
				.register(meterRegistry);
		coalescedMessageCounter = io.micrometer.core.instrument.Counter.builder("ws.message.coalesced")
				.description("토픽 최신값으로 덮어쓴 메시지 수")
				.register(meterRegistry);
		fanoutRecipientsHistogram = DistributionSummary.builder("ws.fanout.recipients")
				.description("이벤트 1건당 fanout 수신자 수")
				.publishPercentileHistogram()
				.register(meterRegistry);
		fanoutDurationTimer = Timer.builder("ws.fanout.duration")
				.description("이벤트 1건 fanout enqueue 완료 시간")
				.publishPercentileHistogram()
				.register(meterRegistry);
		fanoutChunkDurationTimer = Timer.builder("ws.fanout.chunk.duration")
				.description("fanout chunk 처리 시간")
				.publishPercentileHistogram()
				.register(meterRegistry);
	}

	public void publish(CurrentPriceUpdatedEvent event) {
		if (event == null || event.getStockId() == null) {
			return;
		}
		String topic = "quote:" + event.getStockId();
		TextMessage payload = serializeEvent(topic, event);
		if (payload == null) {
			return;
		}

		TopicFanoutState state = topicFanoutStates.computeIfAbsent(topic, ignored -> new TopicFanoutState());
		FanoutEnvelope envelope = new FanoutEnvelope(topic, payload);
		FanoutEnvelope previous = state.latestEnvelope.getAndSet(envelope);
		if (previous != null) {
			coalescedMessageCounter.increment();
		}
		scheduleTopicDrain(topic, state);
	}

	private TextMessage serializeEvent(String topic, CurrentPriceUpdatedEvent event) {
		try {
			return new TextMessage(objectMapper.writeValueAsString(
					new QuoteEventPayload(
							"event",
							topic,
							Instant.now().toEpochMilli(),
							new QuoteEventData(
									event.getStockId(),
									EXCHANGE,
									event.getClose(),
									event.getOpen(),
									event.getHigh(),
									event.getLow(),
									event.getPrevDayChangePct(),
									event.getVolume(),
									event.getValue()
							)
					)
			));
		} catch (Exception ex) {
			serializationErrorCounter.increment();
			log.warn("Failed to serialize websocket event.", ex);
			return null;
		}
	}

	private void scheduleTopicDrain(String topic, TopicFanoutState state) {
		if (!state.draining.compareAndSet(false, true)) {
			return;
		}
		try {
			// topic hash 기반으로 executor/shard를 분리하면 노드 내 fanout worker 또는 외부 fanout service로 확장하기 쉽다.
			fanoutExecutor.execute(() -> drainTopic(topic, state));
		} catch (RejectedExecutionException ex) {
			state.draining.set(false);
			fanoutRejectedCounter.increment();
			log.warn("Rejected websocket fanout task - topic: {}", topic, ex);
		}
	}

	private void drainTopic(String topic, TopicFanoutState state) {
		try {
			while (true) {
				FanoutEnvelope envelope = state.latestEnvelope.getAndSet(null);
				if (envelope == null) {
					return;
				}
				fanout(envelope);
			}
		} finally {
			state.draining.set(false);
			if (state.latestEnvelope.get() != null) {
				scheduleTopicDrain(topic, state);
			} else {
				topicFanoutStates.remove(topic, state);
			}
		}
	}

	private void fanout(FanoutEnvelope envelope) {
		String[] subscriberIds = registry.snapshotSubscriberIds(envelope.topic());
		fanoutRecipientsHistogram.record(subscriberIds.length);
		long fanoutStartNs = System.nanoTime();
		int totalChunks = (subscriberIds.length + fanoutChunkSize - 1) / fanoutChunkSize;
		if (totalChunks <= 1 || maxChunkParallelism <= 1) {
			processChunk(envelope, subscriberIds, 0, subscriberIds.length);
		} else {
			int parallelChunks = Math.min(totalChunks, maxChunkParallelism);
			Future<?>[] futures = new Future<?>[parallelChunks - 1];
			int chunkIndex = 0;
			for (; chunkIndex < parallelChunks - 1; chunkIndex++) {
				int start = chunkIndex * fanoutChunkSize;
				int endExclusive = Math.min(start + fanoutChunkSize, subscriberIds.length);
				final int chunkStart = start;
				final int chunkEnd = endExclusive;
				try {
					futures[chunkIndex] = chunkExecutor.submit(() -> processChunk(envelope, subscriberIds, chunkStart, chunkEnd));
				} catch (RejectedExecutionException ex) {
					fanoutRejectedCounter.increment();
					processChunk(envelope, subscriberIds, chunkStart, chunkEnd);
				}
			}
			for (int start = chunkIndex * fanoutChunkSize; start < subscriberIds.length; start += fanoutChunkSize) {
				int endExclusive = Math.min(start + fanoutChunkSize, subscriberIds.length);
				processChunk(envelope, subscriberIds, start, endExclusive);
			}
			for (Future<?> future : futures) {
				if (future == null) {
					continue;
				}
				try {
					future.get();
				} catch (Exception ex) {
					log.debug("Fanout chunk execution failed - topic: {}", envelope.topic(), ex);
				}
			}
		}

		fanoutDurationTimer.record(System.nanoTime() - fanoutStartNs, TimeUnit.NANOSECONDS);
	}

	private void processChunk(FanoutEnvelope envelope, String[] subscriberIds, int startInclusive, int endExclusive) {
		long chunkStartNs = System.nanoTime();
		for (int index = startInclusive; index < endExclusive; index++) {
			MarketWebSocketConnection connection = registry.getConnection(subscriberIds[index]);
			if (connection == null) {
				continue;
			}
			if (sessionSender.sendQuote(connection, envelope.message())) {
				eventsDispatchedCounter.increment();
			}
		}
		fanoutChunkDurationTimer.record(System.nanoTime() - chunkStartNs, TimeUnit.NANOSECONDS);
	}

	private record TopicFanoutState(
			AtomicReference<FanoutEnvelope> latestEnvelope,
			AtomicBoolean draining
	) {
		private TopicFanoutState() {
			this(new AtomicReference<>(), new AtomicBoolean(false));
		}
	}

	private record FanoutEnvelope(String topic, TextMessage message) {
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
