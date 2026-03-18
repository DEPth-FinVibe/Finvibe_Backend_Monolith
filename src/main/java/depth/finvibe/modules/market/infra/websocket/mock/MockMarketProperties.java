package depth.finvibe.modules.market.infra.websocket.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mock 시세 Provider 설정
 *
 * <pre>
 * market:
 *   provider: mock
 *   mock:
 *     emit-interval-ms: 1000   # 스케줄러 발화 주기 (기본값: 1000ms)
 *     stocks-per-tick: 0       # 틱당 이벤트를 발행할 종목 수 (기본값: 0 = 전체)
 *     publish-threads: 0       # 병렬 발행 스레드 수 (기본값: 0 = CPU 코어 수)
 * </pre>
 */
@ConfigurationProperties(prefix = "market.mock")
@ConditionalOnProperty(name = "market.provider", havingValue = "mock")
public record MockMarketProperties(
		Long emitIntervalMs,
		Integer stocksPerTick,
		Integer publishThreads
) {
	public MockMarketProperties {
		if (emitIntervalMs == null) emitIntervalMs = 1_000L;
		if (stocksPerTick == null || stocksPerTick <= 0) stocksPerTick = 0;
		if (publishThreads == null || publishThreads <= 0)
			publishThreads = Runtime.getRuntime().availableProcessors();
	}
}
