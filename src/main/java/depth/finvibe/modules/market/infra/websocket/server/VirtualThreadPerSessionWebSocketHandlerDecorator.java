package depth.finvibe.modules.market.infra.websocket.server;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Slf4j
public class VirtualThreadPerSessionWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

	private final SerialPerSessionExecutorGroup sessionExecutors;

	public VirtualThreadPerSessionWebSocketHandlerDecorator(
			WebSocketHandler delegate,
			ExecutorService executorService
	) {
		super(delegate);
		this.sessionExecutors = new SerialPerSessionExecutorGroup(executorService);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessionExecutors.execute(session.getId(), () -> invoke(() -> super.afterConnectionEstablished(session), session, true));
	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
		sessionExecutors.execute(session.getId(), () -> invoke(() -> super.handleMessage(session, message), session, false));
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		sessionExecutors.execute(session.getId(), () -> invoke(() -> super.handleTransportError(session, exception), session, false));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
		sessionExecutors.execute(session.getId(), () -> {
			invoke(() -> super.afterConnectionClosed(session, closeStatus), session, false);
			sessionExecutors.clear(session.getId());
		});
	}

	private void invoke(SessionTask task, WebSocketSession session, boolean closeOnFailure) {
		try {
			task.run();
		} catch (Exception ex) {
			log.warn("Virtual-thread websocket handler task failed - sessionId: {}", session.getId(), ex);
			if (closeOnFailure) {
				closeQuietly(session, CloseStatus.SERVER_ERROR);
			}
		}
	}

	private void closeQuietly(WebSocketSession session, CloseStatus status) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.close(status);
		} catch (IOException ex) {
			log.warn("Failed to close websocket session after handler failure - sessionId: {}", session.getId(), ex);
		}
	}

	@FunctionalInterface
	private interface SessionTask {
		void run() throws Exception;
	}
}
