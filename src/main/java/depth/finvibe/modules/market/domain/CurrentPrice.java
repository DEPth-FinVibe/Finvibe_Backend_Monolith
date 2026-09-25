package depth.finvibe.modules.market.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class CurrentPrice {
    private final Long stockId;
    private final LocalDateTime at;
    private final BigDecimal price;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final BigDecimal prevDayChangePct;
    private final BigDecimal volume;
    private final BigDecimal value;
    // 저장소가 부여한다. 직렬화 시 비어 있으면 생략해 저장소가 붙일 자리를 남긴다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Long priceVersion;

    public CurrentPrice withPriceVersion(Long priceVersion) {
        return new CurrentPrice(stockId, at, price, open, high, low, close, prevDayChangePct, volume, value, priceVersion);
    }

    public static CurrentPrice from(PriceCandle priceCandle) {
        return new CurrentPrice(
                priceCandle.getStockId(),
                priceCandle.getAt(),
                priceCandle.getClose(),
                priceCandle.getOpen(),
                priceCandle.getHigh(),
                priceCandle.getLow(),
                priceCandle.getClose(),
                priceCandle.getPrevDayChangePct(),
                priceCandle.getVolume(),
                priceCandle.getValue(),
                null
        );
    }

    public static CurrentPrice from(CurrentPriceUpdatedEvent priceUpdate) {
        return new CurrentPrice(
                priceUpdate.getStockId(),
                priceUpdate.getAt(),
                priceUpdate.getClose(),
                priceUpdate.getOpen(),
                priceUpdate.getHigh(),
                priceUpdate.getLow(),
                priceUpdate.getClose(),
                priceUpdate.getPrevDayChangePct(),
                priceUpdate.getVolume(),
                priceUpdate.getValue(),
                null
        );
    }
}
