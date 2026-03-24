package depth.finvibe.boot.config.investment;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketWebSocketExecutorConfig {

	@Bean(name = "marketWsConnectionExecutor", destroyMethod = "shutdown")
	@ConditionalOnProperty(prefix = "market.ws.connection.virtual-threads", name = "enabled", havingValue = "true")
	public ExecutorService marketWsConnectionExecutor() {
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("market-ws-conn-", 0).factory());
	}

	@Bean(name = "marketWsFanoutExecutor", destroyMethod = "shutdown")
	public ExecutorService marketWsFanoutExecutor(
			@Value("${market.ws.virtual-threads.enabled:false}") boolean virtualThreadsEnabled,
			@Value("${market.ws.fanout.executor.core-size:2}") int coreSize,
			@Value("${market.ws.fanout.executor.max-size:4}") int maxSize,
			@Value("${market.ws.fanout.executor.queue-capacity:256}") int queueCapacity
	) {
		if (virtualThreadsEnabled) {
			return Executors.newVirtualThreadPerTaskExecutor();
		}
		return new ThreadPoolExecutor(
				coreSize,
				maxSize,
				60L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(queueCapacity),
				runnable -> {
					Thread thread = new Thread(runnable);
					thread.setName("market-ws-fanout-" + thread.threadId());
					thread.setDaemon(true);
					return thread;
				},
				new ThreadPoolExecutor.CallerRunsPolicy()
		);
	}

	@Bean(name = "marketWsSendExecutor", destroyMethod = "shutdown")
	public ExecutorService marketWsSendExecutor(
			@Value("${market.ws.virtual-threads.enabled:false}") boolean virtualThreadsEnabled,
			@Value("${market.ws.send.executor.core-size:8}") int coreSize,
			@Value("${market.ws.send.executor.max-size:16}") int maxSize,
			@Value("${market.ws.send.executor.queue-capacity:512}") int queueCapacity
	) {
		if (virtualThreadsEnabled) {
			return Executors.newVirtualThreadPerTaskExecutor();
		}
		return new ThreadPoolExecutor(
				coreSize,
				maxSize,
				60L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(queueCapacity),
				runnable -> {
					Thread thread = new Thread(runnable);
					thread.setName("market-ws-send-" + thread.threadId());
					thread.setDaemon(true);
					return thread;
				},
				new ThreadPoolExecutor.CallerRunsPolicy()
		);
	}
}
