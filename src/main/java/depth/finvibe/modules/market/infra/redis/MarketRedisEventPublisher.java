package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

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
	@Value("${market.redis.pubsub.current-price-updated-mode:classic}")
	private String currentPriceUpdatedMode;
	@Value("${spring.data.redis.cluster.nodes:${SPRING_DATA_REDIS_CLUSTER_NODES:${REDIS_CLUSTER_NODES:}}}")
	private String redisClusterNodes;
	@Value("${spring.data.redis.password:${REDIS_PASSWORD:}}")
	private String redisPassword;

	private RedisClusterClient clusterClient;
	private StatefulRedisClusterConnection<String, String> clusterConnection;

	@PostConstruct
	void initClusterConnection() {
		if (!"sharded".equalsIgnoreCase(currentPriceUpdatedMode)) {
			return;
		}
		if (redisClusterNodes == null || redisClusterNodes.isBlank()) {
			throw new IllegalStateException("Sharded pub/sub mode requires redis cluster nodes");
		}

		List<RedisURI> redisUris = Arrays.stream(redisClusterNodes.split(","))
				.map(String::trim)
				.filter(node -> !node.isEmpty())
				.map(this::toRedisUri)
				.toList();

		this.clusterClient = RedisClusterClient.create(redisUris);
		this.clusterConnection = clusterClient.connect();
	}

	@PreDestroy
	void closeClusterConnection() {
		if (clusterConnection != null) {
			clusterConnection.close();
		}
		if (clusterClient != null) {
			clusterClient.shutdown();
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

	private RedisURI toRedisUri(String node) {
		String[] parts = node.split(":", 2);
		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid redis cluster node: " + node);
		}

		RedisURI.Builder builder = RedisURI.builder()
				.withHost(parts[0])
				.withPort(Integer.parseInt(parts[1]))
				.withTimeout(Duration.ofSeconds(3));

		if (redisPassword != null && !redisPassword.isBlank()) {
			builder.withPassword(redisPassword.toCharArray());
		}

		return builder.build();
	}
}
