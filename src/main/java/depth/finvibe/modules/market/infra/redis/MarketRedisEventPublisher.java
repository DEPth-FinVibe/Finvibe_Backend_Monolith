package depth.finvibe.modules.market.infra.redis;

import depth.finvibe.modules.market.application.port.out.CurrentPriceEventPublisher;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.lettuce.core.RedisFuture;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketRedisEventPublisher implements CurrentPriceEventPublisher {

    private static final String PUBLISH_FAILED_METRIC = "market.current_price.publish_failed";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MarketRedisPipeline marketRedisPipeline;
    private final MeterRegistry meterRegistry;
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

    @Override
    public void publishAll(List<CurrentPriceUpdatedEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        boolean sharded = "sharded".equalsIgnoreCase(currentPriceUpdatedMode);
        if (!sharded || !marketRedisPipeline.isAvailable()) {
            events.forEach(this::publish);
            return;
        }

        // 채널(파티션)마다 틱 배열 하나로 묶는다. 틱은 버리지 않고, 같은 종목은 같은 채널이라 순서가 유지된다.
        Map<String, List<CurrentPriceUpdatedEvent>> byChannel = new LinkedHashMap<>();
        for (CurrentPriceUpdatedEvent event : events) {
            if (event == null || event.getStockId() == null) {
                continue;
            }
            String channel = MarketRedisPubSubTopic.resolveCurrentPriceUpdatedChannel(
                    currentPriceUpdatedTopic,
                    event.getStockId(),
                    currentPriceUpdatedPartitionCount,
                    currentPriceUpdatedMode
            );
            byChannel.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(event);
        }

        Map<String, String> payloads = new LinkedHashMap<>();
        for (Map.Entry<String, List<CurrentPriceUpdatedEvent>> entry : byChannel.entrySet()) {
            try {
                payloads.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
            } catch (JacksonIOException ex) {
                log.warn("Failed to serialize redis event batch. channel={}, size={}", entry.getKey(), entry.getValue().size(), ex);
            }
        }
        // 응답은 기다리지 않는다. 같은 연결의 다음 flush보다 먼저 전송되므로 채널 안 순서는 유지된다.
        marketRedisPipeline.submit(commands -> {
            List<RedisFuture<Long>> submitted = new ArrayList<>(payloads.size());
            payloads.forEach((channel, payload) -> submitted.add(commands.spublish(channel, payload)));
            return submitted;
        }).forEach(future -> future.whenComplete((receivers, ex) -> {
            if (ex != null) {
                meterRegistry.counter(PUBLISH_FAILED_METRIC).increment();
                log.debug("Failed to publish redis event batch.", ex);
            }
        }));
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
