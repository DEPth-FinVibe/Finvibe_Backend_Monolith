package depth.finvibe.common.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutedEvent {
    private Long tradeId;
    private String userId;
    private String type; // "BUY", "SELL"
    private BigDecimal amount;
    private Long price;
    private Long stockId;
    private String name;
    private String currency;
    private Long portfolioId;
}
