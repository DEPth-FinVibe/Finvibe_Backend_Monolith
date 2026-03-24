package depth.finvibe.boot.config.investment;

import depth.finvibe.modules.market.infra.websocket.server.MarketQuoteWebSocketHandler;
import depth.finvibe.modules.market.infra.websocket.server.VirtualThreadPerSessionWebSocketHandlerDecorator;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketQuoteWebSocketHandler marketQuoteWebSocketHandler;
    @Qualifier("marketWsConnectionExecutor")
    private final ObjectProvider<ExecutorService> marketWsConnectionExecutorProvider;

    @Value("${market.ws.connection.virtual-threads.enabled:false}")
    private boolean connectionVirtualThreadsEnabled;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(resolveHandler(), "/market/ws")
                .setAllowedOriginPatterns("*");
    }

    private WebSocketHandler resolveHandler() {
        if (!connectionVirtualThreadsEnabled) {
            return marketQuoteWebSocketHandler;
        }
        ExecutorService executorService = marketWsConnectionExecutorProvider.getIfAvailable();
        if (executorService == null) {
            return marketQuoteWebSocketHandler;
        }
        return new VirtualThreadPerSessionWebSocketHandlerDecorator(marketQuoteWebSocketHandler, executorService);
    }
}
