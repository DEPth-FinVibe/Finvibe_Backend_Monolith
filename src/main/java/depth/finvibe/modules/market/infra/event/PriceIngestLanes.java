package depth.finvibe.modules.market.infra.event;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 시세 틱을 종목별 레인에 나눠 묶음으로 처리합니다.
 * <p>
 * 같은 종목은 언제나 같은 레인이 처리하므로 순서가 유지됩니다. 레인은 큐에 쌓인 틱을 한 번에 꺼내(최대 maxBatch)
 * 저장·발행을 한 번의 왕복으로 묶습니다. 부하가 낮으면 한두 건씩, 높으면 큰 묶음으로 자연스럽게 커집니다.
 * 틱은 버리지 않습니다. 레인 큐가 가득 차면 넣는 쪽(시세 수신 스레드)을 기다리게 합니다.
 */
@Slf4j
@Component
public class PriceIngestLanes {

	private final CurrentPriceCommandUseCase currentPriceCommandUseCase;
	private final MeterRegistry meterRegistry;
	private final int laneCount;
	private final int maxBatch;
	private final int queueCapacity;

	private final List<Lane> lanes = new ArrayList<>();
	private volatile boolean running;

	public PriceIngestLanes(
			CurrentPriceCommandUseCase currentPriceCommandUseCase,
			MeterRegistry meterRegistry,
			@Value("${market.price-ingest.lanes:4}") int laneCount,
			@Value("${market.price-ingest.max-batch:256}") int maxBatch,
			@Value("${market.price-ingest.queue-capacity:20000}") int queueCapacity
	) {
		this.currentPriceCommandUseCase = currentPriceCommandUseCase;
		this.meterRegistry = meterRegistry;
		this.laneCount = Math.max(1, laneCount);
		this.maxBatch = Math.max(1, maxBatch);
		this.queueCapacity = Math.max(1, queueCapacity);
	}

	@PostConstruct
	void start() {
		running = true;
		DistributionSummary batchSize = DistributionSummary.builder("market.price.ingest.batch.size")
				.publishPercentileHistogram()
				.register(meterRegistry);
		Timer batchDuration = Timer.builder("market.price.ingest.batch.duration")
				.publishPercentileHistogram()
				.register(meterRegistry);
		for (int i = 0; i < laneCount; i++) {
			Lane lane = new Lane(i, new ArrayBlockingQueue<>(queueCapacity), batchSize, batchDuration);
			Gauge.builder("market.price.ingest.queue", lane.queue, BlockingQueue::size)
					.tag("lane", String.valueOf(i))
					.register(meterRegistry);
			lane.thread.start();
			lanes.add(lane);
		}
		log.info("Price ingest lanes started. lanes={}, maxBatch={}, queueCapacity={}", laneCount, maxBatch, queueCapacity);
	}

	@PreDestroy
	void stop() {
		running = false;
		for (Lane lane : lanes) {
			lane.thread.interrupt();
		}
		for (Lane lane : lanes) {
			try {
				lane.thread.join(TimeUnit.SECONDS.toMillis(5));
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	@EventListener
	public void onPriceUpdated(CurrentPriceUpdatedEvent event) {
		if (event == null || event.getStockId() == null) {
			return;
		}
		Lane lane = lanes.get(Math.floorMod(event.getStockId().hashCode(), lanes.size()));
		try {
			lane.queue.put(event);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			meterRegistry.counter("market.price.ingest.dropped", "reason", "interrupted").increment();
		}
	}

	private final class Lane {
		private final BlockingQueue<CurrentPriceUpdatedEvent> queue;
		private final DistributionSummary batchSize;
		private final Timer batchDuration;
		private final Thread thread;

		private Lane(int index, BlockingQueue<CurrentPriceUpdatedEvent> queue, DistributionSummary batchSize, Timer batchDuration) {
			this.queue = queue;
			this.batchSize = batchSize;
			this.batchDuration = batchDuration;
			this.thread = Thread.ofPlatform().name("price-lane-" + index).daemon(true).unstarted(this::run);
		}

		private void run() {
			List<CurrentPriceUpdatedEvent> batch = new ArrayList<>(maxBatch);
			while (running || !queue.isEmpty()) {
				try {
					CurrentPriceUpdatedEvent first = queue.poll(200, TimeUnit.MILLISECONDS);
					if (first == null) {
						continue;
					}
					batch.add(first);
					queue.drainTo(batch, maxBatch - 1);
					process(batch);
				} catch (InterruptedException ex) {
					if (!running) {
						queue.drainTo(batch);
						process(batch);
						return;
					}
				} finally {
					batch.clear();
				}
			}
		}

		private void process(List<CurrentPriceUpdatedEvent> batch) {
			if (batch.isEmpty()) {
				return;
			}
			long startedAt = System.nanoTime();
			try {
				currentPriceCommandUseCase.stockPricesUpdated(List.copyOf(batch));
			} catch (RuntimeException ex) {
				meterRegistry.counter("market.price.ingest.failed").increment(batch.size());
				log.warn("Failed to process price batch. size={}", batch.size(), ex);
			} finally {
				batchSize.record(batch.size());
				batchDuration.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
			}
		}
	}
}
