package depth.finvibe.modules.market.infra.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class MarketWebSocketConnectionTest {

	@Test
	void sendFailureAndSlowSendCountersAreTracked() {
		WebSocketSession session = mock(WebSocketSession.class);
		MarketWebSocketConnection connection = new MarketWebSocketConnection(session);

		assertThat(connection.incrementSendFailure()).isEqualTo(1);
		assertThat(connection.incrementSendFailure()).isEqualTo(2);
		assertThat(connection.getTotalSendFailures()).isEqualTo(2);
		assertThat(connection.incrementSlowSend()).isEqualTo(1);
		connection.recordSendSuccess();
		assertThat(connection.incrementSlowSend()).isEqualTo(1);
	}
}
