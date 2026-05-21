package depth.finvibe.common.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class StockHoldingChangedEvent {
    private Long stockId;
    private Long userId;
    private Boolean isHolding;
}
