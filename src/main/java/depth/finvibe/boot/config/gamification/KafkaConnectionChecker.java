package depth.finvibe.boot.config.gamification;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
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

  @Bean
  public ApplicationRunner kafkaStartupHealthCheck() {
    return args -> {
      Properties props = new Properties();
      props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

      try (AdminClient adminClient = AdminClient.create(props)) {
        DescribeClusterResult result = adminClient.describeCluster();
        result.clusterId().get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new IllegalStateException("Kafka connection check failed on startup", e);
      }
    };
  }
}
