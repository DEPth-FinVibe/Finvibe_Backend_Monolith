package depth.finvibe.boot.config.investment;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile("prod")
public class KafkaConnectionChecker {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private final MeterRegistry meterRegistry;

    public KafkaConnectionChecker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public ApplicationRunner kafkaStartupHealthCheck() {
        return args -> {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

            try (AdminClient adminClient = AdminClient.create(props)) {
                DescribeClusterResult result = adminClient.describeCluster();
                result.clusterId().get(5, TimeUnit.SECONDS);
                meterRegistry.counter("startup.dependency.checks", "dependency", "kafka", "result", "success").increment();
            } catch (Exception e) {
                meterRegistry.counter("startup.dependency.checks", "dependency", "kafka", "result", "failure").increment();
                throw new IllegalStateException("Kafka connection check failed on startup", e);
            }
        };
    }
}
