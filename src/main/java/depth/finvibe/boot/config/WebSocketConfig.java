package depth.finvibe.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig {

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 텍스트 메시지 버퍼 크기 제한 (512KB) - 메모리 폭주 방지
        container.setMaxTextMessageBufferSize(512 * 1024);
        // 바이너리 메시지 버퍼 크기 제한 (512KB)
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        // 비동기 전송 타임아웃 (5초) - 응답 없는 클라이언트 차단
        container.setAsyncSendTimeout(5000L);
        // 세션 유휴 타임아웃 (10분)
        container.setMaxSessionIdleTimeout(600000L);
        return container;
    }
}
