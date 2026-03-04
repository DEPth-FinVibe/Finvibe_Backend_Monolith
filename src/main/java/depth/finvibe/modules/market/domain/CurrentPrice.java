package depth.finvibe.modules.market.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                priceCandle.getValue()
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
                priceUpdate.getValue()
        );
    }
}
