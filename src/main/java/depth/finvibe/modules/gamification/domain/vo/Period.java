package depth.finvibe.modules.gamification.domain.vo;

import java.time.LocalDate;

import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
@Embeddable
public class Period {

    private LocalDate startDate;
    private LocalDate endDate;

    public static Period of(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new DomainException(GamificationErrorCode.INVALID_PERIOD_START_DATE_OR_END_DATE);
        }

        if (startDate.isAfter(endDate)) {
            throw new DomainException(GamificationErrorCode.INVALID_PERIOD_START_DATE_IS_GREATER_THAN_END_DATE);
        }

        return Period.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    public static Period ofWeek(LocalDate anyDateInWeek) {
        LocalDate startOfWeek = anyDateInWeek.minusDays(anyDateInWeek.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        return Period.of(startOfWeek, endOfWeek);
    }

    public static Period ofMonth(int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);
        return Period.of(startOfMonth, endOfMonth);
    }
}
