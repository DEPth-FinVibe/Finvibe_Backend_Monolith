package depth.finvibe.modules.market.infra.event;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.concurrent.Executor;

import depth.finvibe.modules.market.application.port.in.ReservationQueryUseCase;
import depth.finvibe.modules.market.application.port.out.ReservationPriceIndex;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class MarketEventConsumerTest {

    @Mock
    private ReservationQueryUseCase reservationQueryUseCase;

    @Mock
    private ReservationPriceIndex reservationPriceIndex;

    @Mock
    private ObjectProvider<Executor> executorProvider;

    private MarketEventConsumer consumer;

    @BeforeEach
    void setUp() {
        when(executorProvider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(Runnable::run);
        consumer = new MarketEventConsumer(reservationQueryUseCase, reservationPriceIndex, executorProvider);
    }

    @Test
    @DisplayName("인덱스가 체결 가능하다고 할 때만 예약을 조회한다")
    void handle_mayTrigger_queriesReservations() {
        when(reservationPriceIndex.mayTrigger(1L, 70_000L)).thenReturn(true);

        consumer.handlePriceUpdateEventForReservation(tick(1L, "70000"));

        verify(reservationQueryUseCase).reservedStockPriceChanged(1L, 70_000L);
    }

    @Test
    @DisplayName("체결될 예약이 없으면 Redis 조회를 하지 않는다")
    void handle_noTrigger_skipsQuery() {
        when(reservationPriceIndex.mayTrigger(1L, 70_000L)).thenReturn(false);

        consumer.handlePriceUpdateEventForReservation(tick(1L, "70000"));

        verify(reservationQueryUseCase, never()).reservedStockPriceChanged(anyLong(), anyLong());
    }

    private static CurrentPriceUpdatedEvent tick(Long stockId, String close) {
        return CurrentPriceUpdatedEvent.builder().stockId(stockId).close(new BigDecimal(close)).build();
    }
}
