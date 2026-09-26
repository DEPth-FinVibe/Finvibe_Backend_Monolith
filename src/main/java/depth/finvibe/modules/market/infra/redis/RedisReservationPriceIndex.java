package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.ReservationPriceIndex;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 종목별 예약 목표가 경계를 메모리에 두는 인덱스입니다.
 * <p>
 * 매수 예약은 가격이 목표가 이하로 내려오면, 매도 예약은 목표가 이상으로 오르면 체결됩니다.
 * 그래서 종목마다 "가장 높은 매수 목표가"와 "가장 낮은 매도 목표가"만 알면, 틱마다 Redis를 조회하지 않고도
 * 체결될 예약이 있을 수 있는지 판단할 수 있습니다. 실제 체결 대상은 기존처럼 Redis에서 조회합니다.
 * <p>
 * 원본은 Redis의 예약 정렬 집합입니다. 예약이 바뀌면 저장소가 변경 알림을 발행하고, 모든 노드가 그 종목만 다시 읽습니다.
 * 알림을 놓칠 때를 대비해 주기적으로 전체를 다시 읽습니다.
 */
@Slf4j
@Component
public class RedisReservationPriceIndex implements ReservationPriceIndex {

	// 예약 종목 집합을 도입하기 전 데이터를 한 번 옮겼는지 표시한다.
	static final String MIGRATED_KEY = "market:reservation:stocks:migrated";

	private final StringRedisTemplate redisTemplate;
	private final ReservationRepositoryImpl reservationRepository;
	private final MarketRedisPipeline marketRedisPipeline;
	private final Map<Long, Bounds> boundsByStock = new ConcurrentHashMap<>();
	private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("reservation-index").daemon(true).factory());
	private final Counter checkedCounter;
	private final Counter triggeredCounter;

	public RedisReservationPriceIndex(
			StringRedisTemplate redisTemplate,
			ReservationRepositoryImpl reservationRepository,
			MarketRedisPipeline marketRedisPipeline,
			MeterRegistry meterRegistry
	) {
		this.redisTemplate = redisTemplate;
		this.reservationRepository = reservationRepository;
		this.marketRedisPipeline = marketRedisPipeline;
		this.checkedCounter = meterRegistry.counter("market.reservation.index.checked");
		this.triggeredCounter = meterRegistry.counter("market.reservation.index.triggered");
		Gauge.builder("market.reservation.index.stocks", boundsByStock, Map::size).register(meterRegistry);
	}

	@PostConstruct
	void start() {
		migrateLegacyStocks();
		reloadAll();
		if (marketRedisPipeline.isAvailable()) {
			// 구독 연결의 입출력 스레드를 막지 않도록 다시 읽기는 별도 스레드에서 한다.
			marketRedisPipeline.subscribe(ReservationRepositoryImpl.CHANGED_CHANNEL,
					message -> refreshExecutor.execute(() -> refreshQuietly(message)));
		}
	}

	@PreDestroy
	void stop() {
		refreshExecutor.shutdownNow();
	}

	@Override
	public boolean mayTrigger(Long stockId, long price) {
		checkedCounter.increment();
		Bounds bounds = boundsByStock.get(stockId);
		if (bounds == null || !bounds.mayTrigger(price)) {
			return false;
		}
		triggeredCounter.increment();
		return true;
	}

	@Scheduled(fixedDelayString = "${market.reservation.index.reload-interval-ms:30000}")
	public void reloadAll() {
		List<Long> stockIds = reservationRepository.findReservedStockIds();
		Set<Long> alive = new HashSet<>(stockIds);
		for (Long stockId : stockIds) {
			refresh(stockId);
		}
		boundsByStock.keySet().removeIf(stockId -> !alive.contains(stockId));
	}

	void refresh(Long stockId) {
		Double maxBuy = firstScore(redisTemplate.opsForZSet()
				.reverseRangeWithScores(ReservationRepositoryImpl.keyForBuyIndex(stockId), 0, 0));
		Double minSell = firstScore(redisTemplate.opsForZSet()
				.rangeWithScores(ReservationRepositoryImpl.keyForSellIndex(stockId), 0, 0));
		if (maxBuy == null && minSell == null) {
			boundsByStock.remove(stockId);
			return;
		}
		boundsByStock.put(stockId, new Bounds(maxBuy, minSell));
	}

	private void refreshQuietly(String message) {
		try {
			refresh(Long.valueOf(message.trim()));
		} catch (RuntimeException ex) {
			log.warn("Failed to refresh reservation index. message={}", message, ex);
		}
	}

	private void migrateLegacyStocks() {
		if (Boolean.TRUE.equals(redisTemplate.hasKey(MIGRATED_KEY))) {
			return;
		}
		List<Long> legacyStockIds = reservationRepository.findReservedStockIdsByKeyScan();
		if (!legacyStockIds.isEmpty()) {
			redisTemplate.opsForSet().add(ReservationRepositoryImpl.RESERVED_STOCKS_KEY,
					legacyStockIds.stream().map(String::valueOf).toArray(String[]::new));
		}
		redisTemplate.opsForValue().set(MIGRATED_KEY, "1");
		log.info("Migrated reserved stock set. stocks={}", legacyStockIds.size());
	}

	private static Double firstScore(Set<ZSetOperations.TypedTuple<String>> tuples) {
		if (tuples == null || tuples.isEmpty()) {
			return null;
		}
		return tuples.iterator().next().getScore();
	}

	record Bounds(Double maxBuyTarget, Double minSellTarget) {
		boolean mayTrigger(long price) {
			return (maxBuyTarget != null && price <= maxBuyTarget)
					|| (minSellTarget != null && price >= minSellTarget);
		}
	}
}
