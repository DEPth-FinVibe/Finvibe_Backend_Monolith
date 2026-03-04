package depth.finvibe.boot.config.investment;

import depth.finvibe.modules.market.infra.websocket.server.MarketQuoteWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketQuoteWebSocketHandler marketQuoteWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketQuoteWebSocketHandler, "/market/ws")
                .setAllowedOriginPatterns("*");
    }
}
