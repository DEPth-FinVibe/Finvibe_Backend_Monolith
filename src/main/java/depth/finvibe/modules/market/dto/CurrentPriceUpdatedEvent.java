package depth.finvibe.modules.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CurrentPriceUpdatedEvent {
    private Long stockId;
    private Long ts;
    private LocalDateTime at;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal prevDayChangePct;
    private BigDecimal volume;
    private BigDecimal value;
}
