package depth.finvibe.boot.config.investment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "finvibe.redis", name = "startup-check", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RedisStartupChecker implements ApplicationRunner {
    private final RedisConnectionFactory connectionFactory;
    private final MeterRegistry meterRegistry;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            if (ping == null) {
                throw new IllegalStateException("Redis ping returned null");
            }
            meterRegistry.counter("startup.dependency.checks", "dependency", "redis", "result", "success").increment();
            log.info("Redis startup check succeeded with ping response: {}", ping);
        } catch (Exception ex) {
            meterRegistry.counter("startup.dependency.checks", "dependency", "redis", "result", "failure").increment();
            log.error("Redis startup check failed", ex);
            throw new IllegalStateException("Redis connection check failed. Application startup aborted.", ex);
        }
    }
}
