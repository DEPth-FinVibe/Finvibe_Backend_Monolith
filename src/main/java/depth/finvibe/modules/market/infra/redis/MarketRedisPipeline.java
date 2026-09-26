package depth.finvibe.modules.market.infra.redis;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 시세 경로 전용 Redis Cluster 파이프라인 연결입니다.
 * <p>
 * 자동 flush를 끈 연결 여러 개를 둡니다. 호출자는 명령을 한 번에 쌓고 flush한 뒤, 락 밖에서 응답을 기다립니다.
 * 그래서 틱마다 왕복을 기다리지 않고, 여러 틱의 명령이 노드별로 한 번에 전송됩니다.
 * <p>
 * 스레드는 처음 쓸 때 연결 하나를 배정받아 계속 그 연결만 씁니다. 레인마다 연결이 달라 입출력 스레드 하나에
 * 인코딩·응답 처리가 몰리지 않고, 한 레인의 저장·발행은 같은 연결로 나가 순서가 유지됩니다.
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

	@Value("${market.price-ingest.redis-connections:4}")
	private int connectionCount;
	@Value("${market.price-ingest.redis-io-threads:4}")
	private int ioThreads;

	private final List<Slot> slots = new ArrayList<>();
	private final Map<Long, Slot> slotByThread = new ConcurrentHashMap<>();
	private final AtomicInteger nextSlot = new AtomicInteger();
	private ClientResources clientResources;
	private RedisClusterClient clusterClient;
	private StatefulRedisClusterPubSubConnection<String, String> pubSubConnection;

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

		clientResources = DefaultClientResources.builder()
				.ioThreadPoolSize(Math.max(2, ioThreads))
				.build();
		clusterClient = RedisClusterClient.create(clientResources, redisUris);
		clusterClient.setOptions(ClusterClientOptions.builder()
				.topologyRefreshOptions(ClusterTopologyRefreshOptions.builder()
						.enableAllAdaptiveRefreshTriggers()
						.enablePeriodicRefresh(Duration.ofSeconds(30))
						.build())
				.build());
		for (int i = 0; i < Math.max(1, connectionCount); i++) {
			StatefulRedisClusterConnection<String, String> connection = clusterClient.connect();
			connection.setAutoFlushCommands(false);
			slots.add(new Slot(connection));
		}
		log.info("Market redis pipeline connected. nodes={}, connections={}, ioThreads={}",
				redisUris.size(), slots.size(), ioThreads);
	}

	@PreDestroy
	void close() {
		if (pubSubConnection != null) {
			pubSubConnection.close();
		}
		for (Slot slot : slots) {
			slot.connection.close();
		}
		if (clusterClient != null) {
			clusterClient.shutdown();
		}
		if (clientResources != null) {
			clientResources.shutdown();
		}
	}

	public boolean isAvailable() {
		return !slots.isEmpty();
	}

	/**
	 * 명령을 쌓고 한 번에 flush합니다. 응답은 돌려받은 future로 락 밖에서 기다립니다.
	 */
	public <T> List<RedisFuture<T>> submit(
			Function<RedisAdvancedClusterAsyncCommands<String, String>, List<RedisFuture<T>>> commands) {
		if (slots.isEmpty()) {
			throw new IllegalStateException("Market redis pipeline is not available");
		}
		Slot slot = slotByThread.computeIfAbsent(Thread.currentThread().threadId(),
				ignored -> slots.get(Math.floorMod(nextSlot.getAndIncrement(), slots.size())));
		slot.lock.lock();
		try {
			List<RedisFuture<T>> futures = commands.apply(slot.connection.async());
			slot.connection.flushCommands();
			return futures;
		} finally {
			slot.lock.unlock();
		}
	}

	/**
	 * 일반 Pub/Sub 채널을 구독합니다. 클러스터에서 PUBLISH는 모든 노드로 전파되므로 한 연결로 충분합니다.
	 */
	public synchronized void subscribe(String channel, Consumer<String> handler) {
		if (clusterClient == null) {
			throw new IllegalStateException("Market redis pipeline is not available");
		}
		if (pubSubConnection == null) {
			pubSubConnection = clusterClient.connectPubSub();
		}
		pubSubConnection.addListener(new RedisPubSubAdapter<>() {
			@Override
			public void message(String receivedChannel, String message) {
				if (channel.equals(receivedChannel)) {
					handler.accept(message);
				}
			}
		});
		pubSubConnection.sync().subscribe(channel);
	}

	private record Slot(StatefulRedisClusterConnection<String, String> connection, ReentrantLock lock) {
		private Slot(StatefulRedisClusterConnection<String, String> connection) {
			this(connection, new ReentrantLock());
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
