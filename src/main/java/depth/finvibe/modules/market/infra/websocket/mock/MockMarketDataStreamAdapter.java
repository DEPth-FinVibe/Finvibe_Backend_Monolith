package depth.finvibe.modules.market.infra.websocket.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.MarketDataStreamPort;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * KIS 없이 로컬/테스트 환경에서 실시간 시세를 시뮬레이션하는 Mock Provider입니다.
 * <p>
 * 활성화: {@code mock-market} 프로파일 적용
 * <p>
 * 스케줄러가 {@code emit-interval-ms}마다 발화하고,
 * 틱당 최대 {@code stocks-per-tick}개 종목의 이벤트를 {@code publish-threads}개 스레드로 병렬 발행합니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "market.provider", havingValue = "mock")
public class MockMarketDataStreamAdapter implements MarketDataStreamPort {

	private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(50_000);
	private static final double MAX_CHANGE_RATE = 0.005; // ±0.5% per tick

	private final ApplicationEventPublisher eventPublisher;
	private final MockMarketProperties properties;
	private final MeterRegistry meterRegistry;

	private final Map<Long, String> subscribedStocks = new ConcurrentHashMap<>();
	private final Map<Long, BigDecimal> currentPrices = new ConcurrentHashMap<>();

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
			r -> new Thread(r, "mock-market-scheduler")
	);
	private ExecutorService publishPool;
	private ScheduledFuture<?> emitTask;
	private volatile boolean initialized;
	private final Counter priceUpdateReceivedCounter;
	private final Counter priceUpdatePublishedCounter;
	private final Timer priceUpdateProcessingTimer;
	private final Timer priceUpdateEventAgeTimer;

	public MockMarketDataStreamAdapter(
			ApplicationEventPublisher eventPublisher,
			MockMarketProperties properties,
			MeterRegistry meterRegistry
	) {
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.priceUpdateReceivedCounter = Counter.builder("market.price.update.provider.received")
				.tag("provider", "mock")
				.description("Provider에서 수신한 PriceUpdate 원본 이벤트 수")
				.register(meterRegistry);
		this.priceUpdatePublishedCounter = Counter.builder("market.price.update.provider.published")
				.tag("provider", "mock")
				.description("Provider에서 애플리케이션 이벤트로 발행한 PriceUpdate 수")
				.register(meterRegistry);
		this.priceUpdateProcessingTimer = Timer.builder("market.price.update.provider.processing")
				.tag("provider", "mock")
				.description("Provider에서 PriceUpdate를 수신 후 애플리케이션 이벤트로 발행하기까지의 처리 시간")
				.publishPercentileHistogram()
				.register(meterRegistry);
		this.priceUpdateEventAgeTimer = Timer.builder("market.price.update.provider.event.age")
				.tag("provider", "mock")
				.description("Provider에서 PriceUpdate를 처리할 때 원본 시각 대비 이벤트 나이")
				.publishPercentileHistogram()
				.register(meterRegistry);
	}

	@Override
	public void initializeSessions() {
		if (initialized) {
			return;
		}
		publishPool = new ThreadPoolExecutor(
				properties.publishThreads(),
				properties.publishThreads(),
				0L,
				TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>(properties.publishQueueCapacity()),
				r -> new Thread(r, "mock-market-publisher"),
				new ThreadPoolExecutor.CallerRunsPolicy()
		);
		log.info("[Mock] 시세 스트림 초기화 — emit: {}ms, stocks-per-tick: {}, threads: {}, queue-capacity: {}",
				properties.emitIntervalMs(),
				properties.stocksPerTick() == 0 ? "전체" : properties.stocksPerTick(),
				properties.publishThreads(),
				properties.publishQueueCapacity());
		startEmitting();
		initialized = true;
	}

	@Override
	public void synchronizeSessions() {
		// no-op
	}

	@Override
	public int removeClosedSessions() {
		return 0;
	}

	@Override
	public void closeAllSessions() {
		// no-op: Mock은 장 시간과 무관하게 항상 활성 상태를 유지합니다.
	}

	@Override
	public int getAvailableSessionCount() {
		return initialized ? 1 : 0;
	}

	@Override
	public void subscribe(Long stockId, String symbol) {
		subscribedStocks.put(stockId, symbol);
		currentPrices.putIfAbsent(stockId, BASE_PRICE);
		log.debug("[Mock] 구독 추가 — stockId: {}, symbol: {}", stockId, symbol);
	}

	@Override
	public void unsubscribe(Long stockId, String symbol) {
		subscribedStocks.remove(stockId);
		currentPrices.remove(stockId);
		log.debug("[Mock] 구독 해제 — stockId: {}, symbol: {}", stockId, symbol);
	}

	@Override
	public boolean isSubscribed(Long stockId) {
		return subscribedStocks.containsKey(stockId);
	}

	@Override
	public Set<Long> getSubscribedStockIds() {
		return Set.copyOf(subscribedStocks.keySet());
	}

	// ── 내부 구현 ─────────────────────────────────────────────────────────────

	private void startEmitting() {
		if (emitTask != null && !emitTask.isDone()) {
			return;
		}
		long interval = properties.emitIntervalMs();
		emitTask = scheduler.scheduleWithFixedDelay(
				this::dispatchTick,
				interval,
				interval,
				TimeUnit.MILLISECONDS
		);
	}

	private void dispatchTick() {
		List<Long> targets = selectTargets();
		for (Long stockId : targets) {
			publishPool.submit(() -> emitOne(stockId));
		}
	}

	/**
	 * stocks-per-tick 설정에 따라 이번 틱에 이벤트를 발행할 종목 목록을 선택합니다.
	 * 0이면 전체, N이면 무작위로 N개를 선택합니다.
	 */
	private List<Long> selectTargets() {
		List<Long> all = new ArrayList<>(subscribedStocks.keySet());
		if (all.isEmpty()) {
			return List.of();
		}
		int limit = properties.stocksPerTick();
		if (limit <= 0 || limit >= all.size()) {
			return all;
		}
		Collections.shuffle(all, ThreadLocalRandom.current());
		return all.subList(0, limit);
	}

	private void emitOne(Long stockId) {
		Timer.Sample sample = Timer.start(meterRegistry);
		priceUpdateReceivedCounter.increment();
		try {
			BigDecimal price = nextPrice(stockId);
			CurrentPriceUpdatedEvent event = buildEvent(stockId, price);
			recordEventAge(event);
			eventPublisher.publishEvent(event);
			priceUpdatePublishedCounter.increment();
		} catch (Exception ex) {
			recordPriceUpdateDropped("emit_failed");
			log.warn("[Mock] 가격 이벤트 발행 실패 — stockId: {}", stockId, ex);
		} finally {
			sample.stop(priceUpdateProcessingTimer);
		}
	}

	private void recordEventAge(CurrentPriceUpdatedEvent event) {
		if (event.getTs() == null) {
			return;
		}

		long ageMillis = Math.max(0L, System.currentTimeMillis() - event.getTs());
		priceUpdateEventAgeTimer.record(Duration.ofMillis(ageMillis));
	}

	private void recordPriceUpdateDropped(String reason) {
		meterRegistry.counter(
				"market.price.update.provider.dropped",
				"provider", "mock",
				"reason", reason
		).increment();
	}

	private BigDecimal nextPrice(Long stockId) {
		BigDecimal current = currentPrices.getOrDefault(stockId, BASE_PRICE);
		double changeRate = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * MAX_CHANGE_RATE;
		BigDecimal next = current.multiply(BigDecimal.valueOf(1 + changeRate))
				.setScale(0, RoundingMode.HALF_UP);
		currentPrices.put(stockId, next);
		return next;
	}

	private CurrentPriceUpdatedEvent buildEvent(Long stockId, BigDecimal close) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BigDecimal spread = close.multiply(BigDecimal.valueOf(0.002)).setScale(0, RoundingMode.HALF_UP);
		BigDecimal prevDayChangePct = BigDecimal.valueOf((rnd.nextDouble() * 6) - 3)
				.setScale(2, RoundingMode.HALF_UP);
		long volume = rnd.nextLong(1_000, 100_000);

		return CurrentPriceUpdatedEvent.builder()
				.stockId(stockId)
				.ts(System.currentTimeMillis())
				.at(LocalDateTime.now())
				.open(close.subtract(spread))
				.high(close.add(spread))
				.low(close.subtract(spread.multiply(BigDecimal.valueOf(2))))
				.close(close)
				.prevDayChangePct(prevDayChangePct)
				.volume(BigDecimal.valueOf(volume))
				.value(close.multiply(BigDecimal.valueOf(volume)))
				.build();
	}
}
