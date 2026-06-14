package depth.finvibe.modules.market.infra.messaging;

import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import depth.finvibe.modules.market.domain.enums.ReservationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketKafkaProducerTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private MarketKafkaProducer marketKafkaProducer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        marketKafkaProducer = new MarketKafkaProducer(kafkaTemplate);
    }

    @Test
    void publishReservationConditionMetEvent_usesStockIdAsKafkaKey() {
        marketKafkaProducer.publishReservationConditionMetEvent(1L, ReservationType.BUY, 123L, 50_000L);

        verify(kafkaTemplate).send(anyString(), org.mockito.ArgumentMatchers.eq("123"), any());
    }

    @Test
    void publishStockPriceUpdated_usesStockIdAsKafkaKey() {
        StockPriceUpdatedEvent event = StockPriceUpdatedEvent.builder()
            .stockId(123L)
            .price(BigDecimal.valueOf(50_000))
            .updatedAt(LocalDateTime.of(2026, 6, 14, 10, 0))
            .build();

        marketKafkaProducer.publishStockPriceUpdated(event);

        verify(kafkaTemplate).send("market.stock-price-updated.v1", "123", event);
    }
}
