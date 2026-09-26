package depth.finvibe.modules.market.infra.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import depth.finvibe.modules.market.application.port.in.CurrentPriceCommandUseCase;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceIngestLanesTest {

    private PriceIngestLanes lanes;

    @AfterEach
    void tearDown() {
        if (lanes != null) {
            lanes.stop();
        }
    }

    @Test
    @DisplayName("모든 틱을 빠짐없이 처리하고, 같은 종목은 넣은 순서대로 처리한다")
    void onPriceUpdated_processesEveryTickInOrderPerStock() throws Exception {
        // given
        int stocks = 20;
        int ticksPerStock = 500;
        CountDownLatch done = new CountDownLatch(stocks * ticksPerStock);
        RecordingUseCase useCase = new RecordingUseCase(done);
        lanes = new PriceIngestLanes(useCase, new SimpleMeterRegistry(), 4, 64, 100);
        lanes.start();

        // when
        for (int seq = 0; seq < ticksPerStock; seq++) {
            for (long stockId = 1; stockId <= stocks; stockId++) {
                lanes.onPriceUpdated(tick(stockId, seq));
            }
        }

        // then
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(useCase.seenByStock).hasSize(stocks);
        useCase.seenByStock.values().forEach(sequence -> {
            assertThat(sequence).hasSize(ticksPerStock);
            assertThat(sequence).isSorted();
        });
        assertThat(useCase.maxBatchSize).isLessThanOrEqualTo(64);
    }

    @Test
    @DisplayName("종료할 때 큐에 남은 틱도 처리한다")
    void stop_drainsRemainingTicks() {
        // given
        CountDownLatch done = new CountDownLatch(100);
        RecordingUseCase useCase = new RecordingUseCase(done);
        lanes = new PriceIngestLanes(useCase, new SimpleMeterRegistry(), 1, 16, 1000);
        lanes.start();
        for (int seq = 0; seq < 100; seq++) {
            lanes.onPriceUpdated(tick(1L, seq));
        }

        // when
        lanes.stop();

        // then
        assertThat(done.getCount()).isZero();
    }

    private static CurrentPriceUpdatedEvent tick(long stockId, int seq) {
        return CurrentPriceUpdatedEvent.builder()
                .stockId(stockId)
                .close(BigDecimal.valueOf(seq))
                .build();
    }

    private static final class RecordingUseCase implements CurrentPriceCommandUseCase {
        private final CountDownLatch done;
        private final Map<Long, List<Integer>> seenByStock = new ConcurrentHashMap<>();
        private volatile int maxBatchSize;

        private RecordingUseCase(CountDownLatch done) {
            this.done = done;
        }

        @Override
        public void stockPricesUpdated(List<CurrentPriceUpdatedEvent> priceUpdates) {
            maxBatchSize = Math.max(maxBatchSize, priceUpdates.size());
            for (CurrentPriceUpdatedEvent event : priceUpdates) {
                seenByStock.computeIfAbsent(event.getStockId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                        .add(event.getClose().intValue());
                done.countDown();
            }
        }

        @Override
        public void stockPriceUpdated(CurrentPriceUpdatedEvent priceUpdate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerWatchingStock(Long stockId, Long userId) {
        }

        @Override
        public void renewWatchingStock(Long stockId, Long userId) {
        }

        @Override
        public void unregisterWatchingStock(Long stockId, Long userId) {
        }

        @Override
        public void registerHoldingStock(Long stockId, Long userId) {
        }

        @Override
        public void unregisterHoldingStock(Long stockId, Long userId) {
        }
    }
}
