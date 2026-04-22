package depth.finvibe.boot.config.gamification;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@ConditionalOnProperty(name = "finvibe.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

	private static final String CLUSTER_MODE = "cluster";

  @Value("${spring.data.redis.host}")
  private String redisHost;

  @Value("${spring.data.redis.port}")
  private int redisPort;

  @Value("${spring.data.redis.password:#{null}}")
  private String redisPassword;

	@Value("${redis.mode:${REDIS_MODE:standalone}}")
	private String redisMode;

	@Value("${spring.data.redis.cluster.nodes:${SPRING_DATA_REDIS_CLUSTER_NODES:${REDIS_CLUSTER_NODES:}}}")
	private String redisClusterNodes;

  @Bean
  public RedissonClient redissonClient() {
		Config config = new Config();

		if (CLUSTER_MODE.equalsIgnoreCase(redisMode) && redisClusterNodes != null && !redisClusterNodes.isBlank()) {
			var clusterConfig = config.useClusterServers()
					.addNodeAddress(clusterNodeAddresses())
					.setScanInterval(5000)
					.setRetryAttempts(3)
					.setRetryInterval(1500)
					.setTimeout(3000)
					.setConnectTimeout(3000)
					.setMasterConnectionPoolSize(32)
					.setMasterConnectionMinimumIdleSize(8)
					.setSlaveConnectionPoolSize(32)
					.setSlaveConnectionMinimumIdleSize(8);

			if (redisPassword != null && !redisPassword.isEmpty()) {
				clusterConfig.setPassword(redisPassword);
			}

			return Redisson.create(config);
		}

		var serverConfig = config.useSingleServer()
				.setAddress("redis://" + redisHost + ":" + redisPort)
				.setConnectionPoolSize(50)
				.setConnectionMinimumIdleSize(10)
				.setRetryAttempts(3)
				.setRetryInterval(1500)
				.setTimeout(3000)
				.setConnectTimeout(3000);

		if (redisPassword != null && !redisPassword.isEmpty()) {
			serverConfig.setPassword(redisPassword);
		}

		return Redisson.create(config);
  }

	private String[] clusterNodeAddresses() {
		return Arrays.stream(redisClusterNodes.split(","))
				.map(String::trim)
				.filter(node -> !node.isEmpty())
				.map(node -> node.startsWith("redis://") ? node : "redis://" + node)
				.toArray(String[]::new);
	}
}
