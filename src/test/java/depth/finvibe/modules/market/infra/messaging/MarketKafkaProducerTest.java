package depth.finvibe.modules.market.infra.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import depth.finvibe.common.investment.dto.StockPriceUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MarketKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("시세 Kafka 발행이 켜져 있으면 종목 키로 시세 이벤트를 보낸다")
    void publishStockPriceUpdated_enabled_sends() {
        MarketKafkaProducer producer = new MarketKafkaProducer(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        producer.publishStockPriceUpdated(event());

        verify(kafkaTemplate).send(eq("market.stock-price-updated.v1"), eq("7"), any());
    }

    @Test
    @DisplayName("시세 Kafka 발행을 끄면 시세 이벤트를 보내지 않는다")
    void publishStockPriceUpdated_disabled_skips() {
        MarketKafkaProducer producer = new MarketKafkaProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "stockPriceKafkaEnabled", false);

        producer.publishStockPriceUpdated(event());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    private StockPriceUpdatedEvent event() {
        return StockPriceUpdatedEvent.builder()
                .stockId(7L)
                .price(new BigDecimal("71200"))
                .updatedAt(LocalDateTime.of(2026, 9, 26, 10, 0))
                .priceVersion(1_790_300_000_000_000L)
                .build();
    }
}
