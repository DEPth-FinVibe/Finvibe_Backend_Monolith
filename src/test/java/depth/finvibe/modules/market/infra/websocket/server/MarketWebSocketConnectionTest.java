package depth.finvibe.modules.market.infra.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class MarketWebSocketConnectionTest {

	@Test
	void enqueueQuoteCoalescesSameTopicWithoutGrowingPendingQueue() {
		WebSocketSession session = mock(WebSocketSession.class);
		MarketWebSocketConnection connection = new MarketWebSocketConnection(session);

		MarketWebSocketConnection.EnqueueResult first =
				connection.enqueueMessage(new TextMessage("{\"price\":100}"), "quote:1", 8);
		MarketWebSocketConnection.EnqueueResult second =
				connection.enqueueMessage(new TextMessage("{\"price\":101}"), "quote:1", 8);

		assertThat(first).isEqualTo(MarketWebSocketConnection.EnqueueResult.ENQUEUED);
		assertThat(second).isEqualTo(MarketWebSocketConnection.EnqueueResult.COALESCED);
		assertThat(connection.getPendingMessages()).isEqualTo(1);
		assertThat(connection.pollPendingMessage().currentMessage().getPayload()).contains("\"price\":101");
		assertThat(connection.getPendingMessages()).isZero();
	}
}
