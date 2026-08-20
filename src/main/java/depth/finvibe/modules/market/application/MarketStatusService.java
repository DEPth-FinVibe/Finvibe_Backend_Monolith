package depth.finvibe.modules.market.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.in.MarketStatusQueryUseCase;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.domain.enums.MarketStatus;
import depth.finvibe.modules.market.domain.enums.MarketStatusReason;
import depth.finvibe.modules.market.domain.enums.TradingDayStatus;
import depth.finvibe.modules.market.dto.MarketStatusDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarketStatusService implements MarketStatusQueryUseCase {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final HolidayCalendarService holidayCalendarService;

    @Override
    public MarketStatusDto.Response getMarketStatus() {
        return getMarketStatusAt(ZonedDateTime.now(KOREA_ZONE));
    }

    MarketStatusDto.Response getMarketStatusAt(ZonedDateTime now) {
        ZonedDateTime nowInKorea = now.withZoneSameInstant(KOREA_ZONE);
        LocalDate date = nowInKorea.toLocalDate();
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return response(MarketStatus.CLOSED, MarketStatusReason.WEEKEND, date, nowInKorea);
        }

        TradingDayStatus tradingDayStatus = holidayCalendarService.getTradingDayStatus(date);
        if (tradingDayStatus == TradingDayStatus.UNKNOWN) {
            return response(MarketStatus.CLOSED, MarketStatusReason.CALENDAR_UNAVAILABLE, date, nowInKorea);
        }
        if (tradingDayStatus == TradingDayStatus.CLOSED) {
            return response(MarketStatus.CLOSED, MarketStatusReason.HOLIDAY, date, nowInKorea);
        }

        MarketStatus status = MarketHours.getStatusAt(nowInKorea);
        MarketStatusReason reason = status == MarketStatus.OPEN
                ? MarketStatusReason.TRADING_HOURS
                : MarketStatusReason.OUTSIDE_TRADING_HOURS;
        return response(status, reason, date, nowInKorea);
    }

    private MarketStatusDto.Response response(
            MarketStatus status,
            MarketStatusReason reason,
            LocalDate date,
            ZonedDateTime now
    ) {
        LocalDate tradingDate = reason == MarketStatusReason.TRADING_HOURS
                ? date
                : holidayCalendarService.getLastCompletedTradingDay(now.toLocalDateTime()).orElse(null);
        return MarketStatusDto.Response.of(status, reason, tradingDate, now.toLocalDateTime());
    }
}
