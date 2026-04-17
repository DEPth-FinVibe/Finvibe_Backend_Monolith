package depth.finvibe.common.investment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class StockPriceUpdatedEvent {
	private Long stockId;
	private BigDecimal price;
	private LocalDateTime updatedAt;
}
