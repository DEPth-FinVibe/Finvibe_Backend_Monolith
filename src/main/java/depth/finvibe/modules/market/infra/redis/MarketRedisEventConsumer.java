package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import depth.finvibe.modules.market.infra.websocket.server.MarketWebSocketPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketRedisEventConsumer {

    private final ObjectMapper objectMapper;
    private final MarketWebSocketPublisher marketWebSocketPublisher;
    private final MeterRegistry meterRegistry;

    private Counter deserializationErrorCounter;

    @PostConstruct
    public void initMetrics() {
        deserializationErrorCounter = Counter.builder("redis.event.deserialization.errors")
                .description("Redis 이벤트 역직렬화 실패 수")
                .register(meterRegistry);
    }

    public void handleMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }

        try {
            CurrentPriceUpdatedEvent event = objectMapper.readValue(payload, CurrentPriceUpdatedEvent.class);
            marketWebSocketPublisher.publish(event);
        } catch (JacksonIOException ex) {
            deserializationErrorCounter.increment();
            log.warn("Failed to deserialize redis event payload.", ex);
        }
    }
}
