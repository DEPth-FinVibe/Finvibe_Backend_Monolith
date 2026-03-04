package depth.finvibe.modules.news.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 뉴스 키워드 카테고리
 * AI가 뉴스 내용을 분석하여 가장 적합한 키워드를 선택
 */
@Getter
@RequiredArgsConstructor
public enum NewsKeyword {
    // 1. 주요 산업 및 섹터 (Industry)
    SEMICONDUCTOR("반도체"),
    BATTERY("이차전지"),
    AI("AI"),
    EV("전기차"),
    BIO("바이오"),
    PLATFORM("플랫폼"),
    SPACE("우주항공"),
    ENTERTAINMENT("엔터테인먼트"),

    // 2. 거시 경제 및 정책 (Macro)
    RATE_FREEZE("금리동결"),
    RATE_HIKE("금리인상"),
    RATE_CUT("금리인하"),
    INFLATION("인플레이션"),
    EXCHANGE_RATE("환율"),
    FOMC("FOMC"),
    RECESSION("경기침체"),
    REAL_ESTATE("부동산"),
    OIL_PRICE("유가"),

    // 3. 투자 스타일 및 테마 (Style)
    DIVIDEND_STOCK("배당주"),
    GROWTH_STOCK("성장주"),
    VALUE_STOCK("가치주"),
    ETF("ETF"),
    IPO("공모주"),
    THEME_STOCK("테마주"),

    // 4. 기업 이벤트 및 이슈 (Event)
    EARNINGS_RELEASE("실적발표"),
    EARNINGS_SURPRISE("어닝서프라이즈"),
    EARNINGS_SHOCK("어닝쇼크"),
    M_AND_A("M&A"),
    BONUS_ISSUE("무상증자"),
    RIGHTS_ISSUE("유상증자"),
    SHAREHOLDERS_MEETING("주주총회"),
    EX_DIVIDEND_DATE("배당락일");

    private final String label;

    public static NewsKeyword fromString(String value) {
        return Arrays.stream(NewsKeyword.values())
                .filter(k -> k.name().equalsIgnoreCase(value) || k.label.equals(value))
                .findFirst()
                .orElse(THEME_STOCK); // 기본값
    }
}
