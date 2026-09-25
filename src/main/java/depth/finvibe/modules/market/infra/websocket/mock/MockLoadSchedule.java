package depth.finvibe.modules.market.infra.websocket.mock;

import java.util.List;

/**
 * 부하 시험용 mock 발행량 증량 스케줄입니다.
 * <p>
 * 단계별 목표 부하(초당 건수)를 {@code stepMillis}마다 한 단계씩 올리고, 마지막 단계에서 멈춥니다.
 * 제어 값으로 즉시 멈추거나(stop), 단계를 고정하거나(hold), 목표 부하를 덮어쓸 수(숫자) 있습니다.
 * 멈춤·고정·덮어쓰기 동안에는 단계가 진행되지 않습니다.
 */
final class MockLoadSchedule {

	static final String STOP = "stop";
	static final String HOLD = "hold";

	private final List<Long> steps;
	private final long stepMillis;

	private long activeMillis;
	private long lastTickAt = -1;
	private double carry;

	MockLoadSchedule(List<Long> steps, long stepMillis) {
		if (steps == null || steps.isEmpty()) {
			throw new IllegalArgumentException("ramp steps must not be empty");
		}
		if (stepMillis <= 0) {
			throw new IllegalArgumentException("step millis must be positive");
		}
		this.steps = List.copyOf(steps);
		this.stepMillis = stepMillis;
	}

	/**
	 * 이번 틱의 목표 부하(초당 건수)를 돌려줍니다. 첫 호출 시점부터 시간이 흐릅니다.
	 */
	long targetRate(long nowMillis, String control) {
		long elapsed = lastTickAt < 0 ? 0 : Math.max(0, nowMillis - lastTickAt);
		lastTickAt = nowMillis;

		String value = control == null ? "" : control.trim();
		if (STOP.equalsIgnoreCase(value)) {
			carry = 0;
			return 0;
		}
		Long override = parseRate(value);
		if (override != null) {
			return override;
		}
		if (!HOLD.equalsIgnoreCase(value)) {
			activeMillis += elapsed;
		}
		return steps.get(stepIndex());
	}

	int stepIndex() {
		return (int) Math.min(activeMillis / stepMillis, steps.size() - 1);
	}

	/**
	 * 목표 부하를 틱 간격에 맞춰 이번 틱에 발행할 건수로 바꿉니다. 소수점 이하는 다음 틱으로 넘깁니다.
	 */
	int emitCount(long ratePerSecond, long intervalMillis) {
		if (ratePerSecond <= 0) {
			return 0;
		}
		double exact = ratePerSecond * intervalMillis / 1000.0 + carry;
		int count = (int) exact;
		carry = exact - count;
		return count;
	}

	private static Long parseRate(String value) {
		if (value.isEmpty()) {
			return null;
		}
		try {
			long rate = Long.parseLong(value);
			return rate >= 0 ? rate : null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
