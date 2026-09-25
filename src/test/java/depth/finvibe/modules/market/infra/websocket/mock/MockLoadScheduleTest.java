package depth.finvibe.modules.market.infra.websocket.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockLoadScheduleTest {

    private static final long STEP = 300_000L;

    @Test
    @DisplayName("첫 호출부터 단계 시간이 흐르고 단계마다 목표 부하가 오르며 마지막 단계에서 멈춘다")
    void targetRate_advancesByStepAndCapsAtLast() {
        MockLoadSchedule schedule = new MockLoadSchedule(List.of(100L, 250L, 500L), STEP);

        assertThat(schedule.targetRate(1_000, null)).isEqualTo(100);
        assertThat(schedule.targetRate(1_000 + STEP - 1, null)).isEqualTo(100);
        assertThat(schedule.targetRate(1_000 + STEP, null)).isEqualTo(250);
        assertThat(schedule.targetRate(1_000 + 2 * STEP, null)).isEqualTo(500);
        assertThat(schedule.targetRate(1_000 + 10 * STEP, null)).isEqualTo(500);
        assertThat(schedule.stepIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("hold 동안에는 단계가 진행되지 않고, 해제하면 이어서 진행한다")
    void targetRate_holdFreezesStep() {
        MockLoadSchedule schedule = new MockLoadSchedule(List.of(100L, 250L), STEP);
        schedule.targetRate(0, null);

        assertThat(schedule.targetRate(STEP * 3, "hold")).isEqualTo(100);
        assertThat(schedule.targetRate(STEP * 3 + STEP - 1, null)).isEqualTo(100);
        assertThat(schedule.targetRate(STEP * 3 + STEP, null)).isEqualTo(250);
    }

    @Test
    @DisplayName("stop이면 발행량 0이고 단계도 진행되지 않는다")
    void targetRate_stopEmitsNothing() {
        MockLoadSchedule schedule = new MockLoadSchedule(List.of(100L, 250L), STEP);
        schedule.targetRate(0, null);

        assertThat(schedule.targetRate(STEP * 2, "STOP")).isZero();
        assertThat(schedule.emitCount(0, 1_000)).isZero();
        assertThat(schedule.targetRate(STEP * 2 + 1, null)).isEqualTo(100);
    }

    @Test
    @DisplayName("숫자 제어 값은 목표 부하를 덮어쓰고, 잘못된 값은 무시한다")
    void targetRate_numericOverride() {
        MockLoadSchedule schedule = new MockLoadSchedule(List.of(100L), STEP);

        assertThat(schedule.targetRate(0, " 1234 ")).isEqualTo(1234);
        assertThat(schedule.targetRate(1, "abc")).isEqualTo(100);
        assertThat(schedule.targetRate(2, "-5")).isEqualTo(100);
    }

    @Test
    @DisplayName("틱당 발행 수는 소수점을 다음 틱으로 넘겨 목표 부하를 정확히 맞춘다")
    void emitCount_carriesFraction() {
        MockLoadSchedule schedule = new MockLoadSchedule(List.of(250L), STEP);

        int total = 0;
        for (int i = 0; i < 20; i++) {
            total += schedule.emitCount(250, 200);
        }

        assertThat(total).isEqualTo(1_000);
        assertThat(schedule.emitCount(1, 200)).isZero();
    }
}
