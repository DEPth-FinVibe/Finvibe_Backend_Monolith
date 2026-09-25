package depth.finvibe.modules.market.infra.websocket.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.market.application.port.out.MarketDataStreamPort;
import depth.finvibe.modules.market.application.port.out.MarketDataSubscriptionResult;
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
	private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");
	private static final double MAX_CHANGE_RATE = 0.005; // ±0.5% per tick

	// 부하 시험 제어 키. stop(중지) / hold(단계 고정) / 숫자(목표 부하 덮어쓰기) / 없음(스케줄대로)
	static final String CONTROL_KEY = "market:mock:control";

	private final ApplicationEventPublisher eventPublisher;
	private final MockMarketProperties properties;
	private final MeterRegistry meterRegistry;
	private final StringRedisTemplate redisTemplate;
	private final MockLoadSchedule loadSchedule;
	private final AtomicLong targetRate = new AtomicLong();
	private final AtomicLong rampStep = new AtomicLong(-1);
	private int roundRobinCursor;

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
			MeterRegistry meterRegistry,
			StringRedisTemplate redisTemplate
	) {
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
		this.redisTemplate = redisTemplate;
		this.loadSchedule = properties.rampEnabled()
				? new MockLoadSchedule(properties.rampSteps(), properties.rampStepSeconds() * 1000)
				: null;
		Gauge.builder("market.mock.target.rate", targetRate, AtomicLong::get)
				.description("부하 시험 mock의 목표 발행량(초당 건수)")
				.register(meterRegistry);
		Gauge.builder("market.mock.ramp.step", rampStep, AtomicLong::get)
				.description("부하 시험 mock의 현재 증량 단계(0부터, -1은 스케줄 미사용·미시작)")
				.register(meterRegistry);
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
		if (properties.rampEnabled()) {
			log.info("[Mock] 부하 증량 스케줄 — steps: {}/s, step: {}s, control key: {}",
					properties.rampSteps(), properties.rampStepSeconds(), CONTROL_KEY);
		}
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
	public int getSubscriptionCapacity() {
		return initialized ? Integer.MAX_VALUE : 0;
	}

	@Override
	public int getRemainingSubscriptionCapacity() {
		return getSubscriptionCapacity();
	}

	@Override
	public MarketDataSubscriptionResult subscribe(Long stockId, String symbol) {
		if (subscribedStocks.containsKey(stockId)) {
			return MarketDataSubscriptionResult.ALREADY_SUBSCRIBED;
		}
		subscribedStocks.put(stockId, symbol);
		currentPrices.putIfAbsent(stockId, BASE_PRICE);
		log.debug("[Mock] 구독 추가 — stockId: {}, symbol: {}", stockId, symbol);
		return MarketDataSubscriptionResult.SUBSCRIBED;
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
		List<Long> targets = loadSchedule != null ? selectScheduledTargets() : selectTargets();
		for (Long stockId : targets) {
			publishPool.submit(() -> emitOne(stockId));
		}
	}

	/**
	 * 증량 스케줄의 목표 부하만큼 구독 종목을 순서대로 돌아가며 고릅니다.
	 * 구독 종목이 처음 생긴 틱부터 스케줄 시간이 흐릅니다.
	 */
	private List<Long> selectScheduledTargets() {
		List<Long> all = new ArrayList<>(subscribedStocks.keySet());
		if (all.isEmpty()) {
			return List.of();
		}
		long rate = loadSchedule.targetRate(System.currentTimeMillis(), readControl());
		targetRate.set(rate);
		rampStep.set(loadSchedule.stepIndex());
		int count = loadSchedule.emitCount(rate, properties.emitIntervalMs());
		List<Long> targets = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			targets.add(all.get(Math.floorMod(roundRobinCursor++, all.size())));
		}
		return targets;
	}

	private String readControl() {
		try {
			return redisTemplate.opsForValue().get(CONTROL_KEY);
		} catch (Exception ex) {
			log.warn("[Mock] 부하 제어 키 조회 실패 — 스케줄대로 진행합니다.", ex);
			return null;
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
				// KIS 실시간 틱과 같은 KST wall clock이어야 priceVersion이 실제 체결시각과 맞는다(운영 JVM은 UTC).
				.at(LocalDateTime.now(MARKET_ZONE))
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
