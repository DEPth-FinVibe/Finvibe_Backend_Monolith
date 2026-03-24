package depth.finvibe.modules.market.infra.websocket.server;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Slf4j
public class VirtualThreadPerSessionWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

	private final ExecutorService executorService;
	private final ConcurrentHashMap<String, SerialPerSessionExecutor> sessionExecutors = new ConcurrentHashMap<>();

	public VirtualThreadPerSessionWebSocketHandlerDecorator(
			WebSocketHandler delegate,
			ExecutorService executorService
	) {
		super(delegate);
		this.executorService = executorService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		executorFor(session.getId()).execute(() -> invoke(() -> super.afterConnectionEstablished(session), session, true));
	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
		executorFor(session.getId()).execute(() -> invoke(() -> super.handleMessage(session, message), session, false));
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		executorFor(session.getId()).execute(() -> invoke(() -> super.handleTransportError(session, exception), session, false));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
		SerialPerSessionExecutor serialExecutor = executorFor(session.getId());
		serialExecutor.execute(() -> {
			invoke(() -> super.afterConnectionClosed(session, closeStatus), session, false);
			sessionExecutors.remove(session.getId(), serialExecutor);
		});
	}

	private SerialPerSessionExecutor executorFor(String sessionId) {
		return sessionExecutors.computeIfAbsent(sessionId, key -> new SerialPerSessionExecutor(executorService));
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

	private static final class SerialPerSessionExecutor {

		private final ExecutorService executorService;
		private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
		private final AtomicBoolean running = new AtomicBoolean(false);

		private SerialPerSessionExecutor(ExecutorService executorService) {
			this.executorService = executorService;
		}

		private void execute(Runnable task) {
			tasks.offer(task);
			drain();
		}

		private void drain() {
			if (!running.compareAndSet(false, true)) {
				return;
			}
			executorService.execute(this::runTasks);
		}

		private void runTasks() {
			try {
				Runnable task;
				while ((task = tasks.poll()) != null) {
					task.run();
				}
			} finally {
				running.set(false);
				if (!tasks.isEmpty()) {
					drain();
				}
			}
		}
	}
}
