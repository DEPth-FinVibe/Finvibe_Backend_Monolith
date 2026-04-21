package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
	@Value("${market.redis.pubsub.current-price-updated-topic:market:price-updated}")
	private String currentPriceUpdatedTopic;
	@Value("${market.redis.pubsub.current-price-updated-partition-count:1}")
	private int currentPriceUpdatedPartitionCount;

    @Override
    public void publish(CurrentPriceUpdatedEvent event) {
        if (event == null || event.getStockId() == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
			String channel = MarketRedisPubSubTopic.resolveCurrentPriceUpdatedChannel(
					currentPriceUpdatedTopic,
					event.getStockId(),
					currentPriceUpdatedPartitionCount
			);
            redisTemplate.convertAndSend(channel, payload);
        } catch (JacksonIOException ex) {
            log.warn("Failed to serialize redis event for stockId={}", event.getStockId(), ex);
        }
    }
}
