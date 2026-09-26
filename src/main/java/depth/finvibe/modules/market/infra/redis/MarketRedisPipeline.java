package depth.finvibe.modules.market.infra.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * 시세 경로 전용 Redis Cluster 파이프라인 연결입니다.
 * <p>
 * 자동 flush를 끈 연결 하나를 공유합니다. 호출자는 명령을 한 번에 쌓고 flush한 뒤, 락 밖에서 응답을 기다립니다.
 * 그래서 틱마다 왕복을 기다리지 않고, 여러 틱의 명령이 노드별로 한 번에 전송됩니다.
 * 클러스터 모드가 아니면(로컬 등) 사용할 수 없고, 호출자는 기존 동기 경로를 씁니다.
 */
@Slf4j
@Component
public class MarketRedisPipeline {

	private static final String CLUSTER_MODE = "cluster";

	@Value("${redis.mode:${REDIS_MODE:standalone}}")
	private String redisMode;
	@Value("${spring.data.redis.cluster.nodes:${SPRING_DATA_REDIS_CLUSTER_NODES:${REDIS_CLUSTER_NODES:}}}")
	private String redisClusterNodes;
	@Value("${spring.data.redis.password:${REDIS_PASSWORD:}}")
	private String redisPassword;

	private final ReentrantLock submitLock = new ReentrantLock();
	private RedisClusterClient clusterClient;
	private StatefulRedisClusterConnection<String, String> connection;

	@PostConstruct
	void connect() {
		if (!CLUSTER_MODE.equalsIgnoreCase(redisMode) || redisClusterNodes == null || redisClusterNodes.isBlank()) {
			log.info("Market redis pipeline disabled (redis.mode={}).", redisMode);
			return;
		}
		List<RedisURI> redisUris = Arrays.stream(redisClusterNodes.split(","))
				.map(String::trim)
				.filter(node -> !node.isEmpty())
				.map(this::toRedisUri)
				.toList();

		clusterClient = RedisClusterClient.create(redisUris);
		clusterClient.setOptions(ClusterClientOptions.builder()
				.topologyRefreshOptions(ClusterTopologyRefreshOptions.builder()
						.enableAllAdaptiveRefreshTriggers()
						.enablePeriodicRefresh(Duration.ofSeconds(30))
						.build())
				.build());
		connection = clusterClient.connect();
		connection.setAutoFlushCommands(false);
		log.info("Market redis pipeline connected. nodes={}", redisUris.size());
	}

	@PreDestroy
	void close() {
		if (connection != null) {
			connection.close();
		}
		if (clusterClient != null) {
			clusterClient.shutdown();
		}
	}

	public boolean isAvailable() {
		return connection != null;
	}

	/**
	 * 명령을 쌓고 한 번에 flush합니다. 응답은 돌려받은 future로 락 밖에서 기다립니다.
	 */
	public <T> List<RedisFuture<T>> submit(
			Function<RedisAdvancedClusterAsyncCommands<String, String>, List<RedisFuture<T>>> commands) {
		if (connection == null) {
			throw new IllegalStateException("Market redis pipeline is not available");
		}
		submitLock.lock();
		try {
			List<RedisFuture<T>> futures = commands.apply(connection.async());
			connection.flushCommands();
			return futures;
		} finally {
			submitLock.unlock();
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
