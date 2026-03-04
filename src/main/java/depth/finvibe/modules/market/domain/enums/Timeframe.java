package depth.finvibe.modules.market.domain.enums;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 캔들 데이터의 조회/저장 기준이 되는 시간 단위.
 */
public enum Timeframe {
    MINUTE, // 분봉
    DAY, // 일봉
    WEEK, // 주봉
    MONTH, // 월봉
    YEAR, // 년봉
    ;

    /**
     * 기준 시간의 시작 시각을 타임프레임 경계로 정규화한다.
     */
    public LocalDateTime normalizeStart(LocalDateTime time) {
        return switch (this) {
            case MINUTE -> time.withSecond(0).withNano(0);
            case DAY -> time.withHour(0).withMinute(0).withSecond(0).withNano(0);
            case WEEK -> time.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case MONTH -> time.with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case YEAR -> time.with(TemporalAdjusters.firstDayOfYear())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        };
    }

    /**
     * 기준 시간의 종료 시각을 타임프레임 경계로 정규화한다.
     */
    public LocalDateTime normalizeEnd(LocalDateTime time) {
        LocalDateTime aligned = normalizeStart(time);
        return switch (this) {
            case MINUTE -> aligned;
            case DAY -> aligned.isBefore(time) ? aligned.plusDays(1) : aligned;
            case WEEK -> aligned.isBefore(time) ? aligned.plusWeeks(1) : aligned;
            case MONTH -> aligned.isBefore(time) ? aligned.plusMonths(1) : aligned;
            case YEAR -> aligned.isBefore(time) ? aligned.plusYears(1) : aligned;
        };
    }

    /**
     * 주어진 시각에서 다음 캔들 시작 시각을 반환한다.
     */
    public LocalDateTime nextTime(LocalDateTime time) {
        return switch (this) {
            case MINUTE -> time.plusMinutes(1);
            case DAY -> time.plusDays(1);
            case WEEK -> time.plusWeeks(1);
            case MONTH -> time.plusMonths(1);
            case YEAR -> time.plusYears(1);
        };
    }

    /**
     * 현재 시각 기준으로 완료된 마지막 캔들 시각을 반환한다.
     */
    public LocalDateTime lastCompletedTime(LocalDateTime now) {
        return switch (this) {
            case MINUTE -> now.minusMinutes(1).withSecond(0).withNano(0);
            case DAY -> now.minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case WEEK -> now.minusWeeks(1).with(DayOfWeek.MONDAY)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case MONTH -> now.minusMonths(1).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case YEAR -> now.minusYears(1).withMonth(1).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        };
    }
}
