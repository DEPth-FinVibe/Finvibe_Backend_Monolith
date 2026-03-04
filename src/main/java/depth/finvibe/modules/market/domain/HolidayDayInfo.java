package depth.finvibe.modules.market.domain;

import java.time.LocalDate;

/**
 * KIS 국내휴장일조회 API 기준일 1건 정보.
 * application 계층에서 KisDto에 의존하지 않기 위한 도메인 타입.
 */
public record HolidayDayInfo(LocalDate date, boolean openDay) {
}
