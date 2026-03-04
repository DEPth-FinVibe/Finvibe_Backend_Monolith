package depth.finvibe.modules.market.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import depth.finvibe.modules.market.dto.PriceCandleDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class BatchUpdatePrice {
  private final Long stockId;
  private final LocalDateTime at;
  private final BigDecimal price;
  private final BigDecimal open;
  private final BigDecimal high;
  private final BigDecimal low;
  private final BigDecimal prevDayChangePct;
  private final BigDecimal volume;
  private final BigDecimal value;

  public static BatchUpdatePrice from(PriceCandleDto.Response priceCandle) {
    return BatchUpdatePrice.builder()
            .stockId(priceCandle.getStockId())
            .at(priceCandle.getAt())
            .price(priceCandle.getClose())
            .open(priceCandle.getOpen())
            .high(priceCandle.getHigh())
            .low(priceCandle.getLow())
            .prevDayChangePct(priceCandle.getPrevDayChangePct())
            .volume(priceCandle.getVolume())
            .value(priceCandle.getValue())
            .build();
  }

  public static BatchUpdatePrice from(CurrentPrice currentPrice) {
    return BatchUpdatePrice.builder()
            .stockId(currentPrice.getStockId())
            .at(currentPrice.getAt())
            .price(currentPrice.getPrice())
            .open(currentPrice.getOpen())
            .high(currentPrice.getHigh())
            .low(currentPrice.getLow())
            .prevDayChangePct(currentPrice.getPrevDayChangePct())
            .volume(currentPrice.getVolume())
            .value(currentPrice.getValue())
            .build();
  }
}
