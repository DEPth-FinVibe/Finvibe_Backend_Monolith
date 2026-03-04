package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import depth.finvibe.modules.market.infra.websocket.server.MarketWebSocketPublisher;
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

    public void handleMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }

        try {
            CurrentPriceUpdatedEvent event = objectMapper.readValue(payload, CurrentPriceUpdatedEvent.class);
            marketWebSocketPublisher.publish(event);
        } catch (JacksonIOException ex) {
            log.warn("Failed to deserialize redis event payload.", ex);
        }
    }
}
