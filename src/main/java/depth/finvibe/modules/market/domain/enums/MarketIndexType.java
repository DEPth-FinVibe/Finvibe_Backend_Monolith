package depth.finvibe.modules.market.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketIndexType {
    KOSPI("0001", "INDEX_KOSPI", "코스피"),
    KOSDAQ("1001", "INDEX_KOSDAQ", "코스닥");

    private final String kisCode;
    private final String symbol;
    private final String displayName;
}
