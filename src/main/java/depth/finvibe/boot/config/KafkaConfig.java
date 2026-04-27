package depth.finvibe.boot.config;

import java.util.HashMap;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.LoggingProducerListener;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory(MeterRegistry meterRegistry) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        configProps.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        // At-least-once delivery guarantees (Kafka 3.7 defaults, made explicit)
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32_768);

        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(configProps);
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        template.setProducerListener(new LoggingProducerListener<>());
        return template;
    }

    @Bean
    public NewTopic stockPriceUpdatedTopic() {
        return TopicBuilder.name("market.stock-price-updated.v1")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
            .config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.5")
            .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "86400000")
            .build();
    }

    @Bean
    public NewTopic stockPriceUpdatedDltTopic() {
        return TopicBuilder.name("market.stock-price-updated.v1.DLT")
            .partitions(6)
            .replicas(1)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
            .build();
    }
}
