package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketRedisEventPublisher implements CurrentPriceEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(CurrentPriceUpdatedEvent event) {
        if (event == null || event.getStockId() == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(MarketRedisPubSubTopic.CURRENT_PRICE_UPDATED, payload);
        } catch (JacksonIOException ex) {
            log.warn("Failed to serialize redis event for stockId={}", event.getStockId(), ex);
        }
    }
}
