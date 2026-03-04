package depth.finvibe.modules.market.infra.config;

import depth.finvibe.modules.market.infra.redis.MarketRedisEventConsumer;
import depth.finvibe.modules.market.infra.redis.MarketRedisPubSubTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@ConditionalOnProperty(name = "finvibe.redis.enabled", havingValue = "true", matchIfMissing = true)
public class MarketRedisPubSubConfig {

    @Bean
    public ChannelTopic currentPriceUpdatedTopic() {
        return new ChannelTopic(MarketRedisPubSubTopic.CURRENT_PRICE_UPDATED);
    }

    @Bean
    public MessageListenerAdapter marketRedisEventListenerAdapter(MarketRedisEventConsumer marketRedisEventConsumer) {
        return new MessageListenerAdapter(marketRedisEventConsumer, "handleMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter marketRedisEventListenerAdapter,
            ChannelTopic currentPriceUpdatedTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(marketRedisEventListenerAdapter, currentPriceUpdatedTopic);
        return container;
    }
}
