package depth.finvibe.modules.market.infra.messaging;

import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import depth.finvibe.modules.market.domain.enums.ReservationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @DisplayName("예약 조건 충족 이벤트를 stockId Kafka key로 발행한다")
    void publishReservationConditionMetEvent_validEvent_usesStockIdKey() {
        // when
        marketKafkaProducer.publishReservationConditionMetEvent(1L, ReservationType.BUY, 123L, 50_000L);

        // then
        verify(kafkaTemplate).send(anyString(), org.mockito.ArgumentMatchers.eq("123"), any());
    }

    @Test
    @DisplayName("주식 가격 변경 이벤트를 stockId Kafka key로 발행한다")
    void publishStockPriceUpdated_validEvent_usesStockIdKey() {
        // given
        StockPriceUpdatedEvent event = StockPriceUpdatedEvent.builder()
            .stockId(123L)
            .price(BigDecimal.valueOf(50_000))
            .updatedAt(LocalDateTime.of(2026, 6, 14, 10, 0))
            .build();

        // when
        marketKafkaProducer.publishStockPriceUpdated(event);

        // then
        verify(kafkaTemplate).send("market.stock-price-updated.v1", "123", event);
    }

    @Test
    @DisplayName("시세 Kafka 발행을 끄면 주식 가격 변경 이벤트를 보내지 않는다")
    void publishStockPriceUpdated_disabled_skips() {
        // given
        ReflectionTestUtils.setField(marketKafkaProducer, "stockPriceKafkaEnabled", false);
        StockPriceUpdatedEvent event = StockPriceUpdatedEvent.builder()
            .stockId(123L)
            .price(BigDecimal.valueOf(50_000))
            .updatedAt(LocalDateTime.of(2026, 6, 14, 10, 0))
            .build();

        // when
        marketKafkaProducer.publishStockPriceUpdated(event);

        // then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
