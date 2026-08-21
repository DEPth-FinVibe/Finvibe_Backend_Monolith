package depth.finvibe.modules.market.infra.websocket.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import depth.finvibe.modules.market.application.port.out.MarketDataSubscriptionResult;
import depth.finvibe.modules.market.infra.client.KisCredentialAllocator;
import depth.finvibe.modules.market.infra.client.KisRateLimiter;
import depth.finvibe.modules.market.infra.client.MarketServiceClient;
import depth.finvibe.modules.market.infra.config.KisCredentialsProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class KisConnectionPoolTest {

    private KisConnectionPool connectionPool;
    private Map<String, KisWebsocketSession> sessions;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connectionPool = new KisConnectionPool(
                mock(KisCredentialsProperties.class),
                mock(KisCredentialAllocator.class),
                mock(KisRateLimiter.class),
                mock(MarketServiceClient.class),
                mock(ObjectMapper.class),
                mock(ApplicationEventPublisher.class),
                new SimpleMeterRegistry()
        );
        sessions = (Map<String, KisWebsocketSession>) ReflectionTestUtils.getField(connectionPool, "sessions");
    }

    @Test
    @DisplayName("연결된 세션이 없으면 NO_SESSION을 반환하고 매핑을 남기지 않는다")
    void subscribe_noConnectedSession_returnsNoSession() {
        // when
        MarketDataSubscriptionResult result = connectionPool.subscribe(1L, "005930");

        // then
        assertThat(result).isEqualTo(MarketDataSubscriptionResult.NO_SESSION);
        assertThat(connectionPool.isSubscribed(1L)).isFalse();
    }

    @Test
    @DisplayName("연결된 모든 세션이 가득 차면 NO_CAPACITY를 반환한다")
    void subscribe_fullSession_returnsNoCapacity() {
        // given
        KisWebsocketSession session = session(true, 41);
        sessions.put("app-key", session);

        // when
        MarketDataSubscriptionResult result = connectionPool.subscribe(1L, "005930");

        // then
        assertThat(result).isEqualTo(MarketDataSubscriptionResult.NO_CAPACITY);
        assertThat(connectionPool.isSubscribed(1L)).isFalse();
    }

    @Test
    @DisplayName("연결 세션으로 구독 요청을 전송하면 SUBSCRIBED를 반환한다")
    void subscribe_availableSession_returnsSubscribed() {
        // given
        KisWebsocketSession session = session(true, 10);
        sessions.put("app-key", session);

        // when
        MarketDataSubscriptionResult result = connectionPool.subscribe(1L, "005930");

        // then
        assertThat(result).isEqualTo(MarketDataSubscriptionResult.SUBSCRIBED);
        assertThat(connectionPool.isSubscribed(1L)).isTrue();
        verify(session).subscribe("005930");
    }

    @Test
    @DisplayName("구독 전송이 실패하면 SEND_FAILED를 반환하고 매핑을 롤백한다")
    void subscribe_sendFailure_returnsSendFailed() {
        // given
        KisWebsocketSession session = session(true, 10);
        doThrow(new IllegalStateException("send failed")).when(session).subscribe("005930");
        sessions.put("app-key", session);

        // when
        MarketDataSubscriptionResult result = connectionPool.subscribe(1L, "005930");

        // then
        assertThat(result).isEqualTo(MarketDataSubscriptionResult.SEND_FAILED);
        assertThat(connectionPool.isSubscribed(1L)).isFalse();
    }

    @Test
    @DisplayName("실제 연결 세션만 총 용량과 남은 슬롯에 포함한다")
    void capacity_countsOnlyConnectedSessions() {
        // given
        sessions.put("connected", session(true, 10));
        sessions.put("closed", session(false, 20));

        // when & then
        assertThat(connectionPool.getAvailableSessionCount()).isEqualTo(1);
        assertThat(connectionPool.getSubscriptionCapacity()).isEqualTo(41);
        assertThat(connectionPool.getRemainingSubscriptionCapacity()).isEqualTo(31);
    }

    private KisWebsocketSession session(boolean connected, int subscriptionCount) {
        KisWebsocketSession session = mock(KisWebsocketSession.class);
        when(session.getIsConnected()).thenReturn(connected);
        when(session.getSubscriptionCount()).thenReturn(subscriptionCount);
        return session;
    }
}
