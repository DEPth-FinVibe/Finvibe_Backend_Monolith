package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
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
	private final LettuceConnectionFactory lettuceConnectionFactory;
	@Value("${market.redis.pubsub.current-price-updated-topic:market:price-updated}")
	private String currentPriceUpdatedTopic;
	@Value("${market.redis.pubsub.current-price-updated-partition-count:1}")
	private int currentPriceUpdatedPartitionCount;
	@Value("${market.redis.pubsub.current-price-updated-mode:classic}")
	private String currentPriceUpdatedMode;

	private StatefulRedisClusterConnection<String, String> clusterConnection;

	@PostConstruct
	void initClusterConnection() {
		if (!"sharded".equalsIgnoreCase(currentPriceUpdatedMode)) {
			return;
		}

		Object nativeClient = lettuceConnectionFactory.getRequiredNativeClient();
		if (!(nativeClient instanceof RedisClusterClient redisClusterClient)) {
			throw new IllegalStateException("Sharded pub/sub mode requires RedisClusterClient");
		}

		this.clusterConnection = redisClusterClient.connect();
	}

	@PreDestroy
	void closeClusterConnection() {
		if (clusterConnection != null) {
			clusterConnection.close();
		}
	}

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
					currentPriceUpdatedPartitionCount,
					currentPriceUpdatedMode
			);
			if ("sharded".equalsIgnoreCase(currentPriceUpdatedMode)) {
				if (clusterConnection == null) {
					throw new IllegalStateException("Cluster connection not initialized for sharded pub/sub mode");
				}
				clusterConnection.sync().spublish(channel, payload);
				return;
			}

			redisTemplate.convertAndSend(channel, payload);
		} catch (JacksonIOException ex) {
			log.warn("Failed to serialize redis event for stockId={}", event.getStockId(), ex);
		}
    }
}
