package depth.finvibe.modules.market.infra.websocket.server;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class SerialPerSessionExecutorGroup {

	private final Executor executor;
	private final ConcurrentHashMap<String, SerialPerSessionExecutor> sessionExecutors = new ConcurrentHashMap<>();

	public SerialPerSessionExecutorGroup(Executor executor) {
		this.executor = executor;
	}

	public void execute(String sessionId, Runnable task) {
		executorFor(sessionId).execute(task);
	}

	public void clear(String sessionId) {
		sessionExecutors.remove(sessionId);
	}

	private SerialPerSessionExecutor executorFor(String sessionId) {
		return sessionExecutors.computeIfAbsent(sessionId, key -> new SerialPerSessionExecutor(executor));
	}

	private static final class SerialPerSessionExecutor {

		private final Executor executor;
		private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
		private final AtomicBoolean running = new AtomicBoolean(false);

		private SerialPerSessionExecutor(Executor executor) {
			this.executor = executor;
		}

		private void execute(Runnable task) {
			tasks.offer(task);
			drain();
		}

		private void drain() {
			if (!running.compareAndSet(false, true)) {
				return;
			}
			executor.execute(this::runTasks);
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
