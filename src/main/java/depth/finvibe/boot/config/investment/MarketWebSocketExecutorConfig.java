package depth.finvibe.boot.config.investment;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MarketWebSocketExecutorConfig {

	@Bean(name = "marketWsFanoutExecutor")
	public Executor marketWsFanoutExecutor(
			@Value("${market.ws.fanout.executor.core-size:2}") int coreSize,
			@Value("${market.ws.fanout.executor.max-size:4}") int maxSize,
			@Value("${market.ws.fanout.executor.queue-capacity:256}") int queueCapacity
	) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(coreSize);
		executor.setMaxPoolSize(maxSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("market-ws-fanout-");
		executor.initialize();
		return executor;
	}

	@Bean(name = "marketWsSendExecutor")
	public Executor marketWsSendExecutor(
			@Value("${market.ws.send.executor.core-size:8}") int coreSize,
			@Value("${market.ws.send.executor.max-size:16}") int maxSize,
			@Value("${market.ws.send.executor.queue-capacity:512}") int queueCapacity
	) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(coreSize);
		executor.setMaxPoolSize(maxSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("market-ws-send-");
		executor.initialize();
		return executor;
	}
}
