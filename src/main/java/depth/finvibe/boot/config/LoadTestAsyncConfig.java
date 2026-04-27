package depth.finvibe.boot.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Profile("loadtest")
public class LoadTestAsyncConfig {

	@Primary
	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(16);
		executor.setMaxPoolSize(32);
		executor.setQueueCapacity(4096);
		executor.setThreadNamePrefix("loadtest-async-");
		executor.initialize();
		return executor;
	}
}
