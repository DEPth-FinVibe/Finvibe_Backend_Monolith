package depth.finvibe.modules.market.infra.websocket.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "market.provider", havingValue = "mock")
@EnableConfigurationProperties(MockMarketProperties.class)
public class MockMarketConfig {
}
