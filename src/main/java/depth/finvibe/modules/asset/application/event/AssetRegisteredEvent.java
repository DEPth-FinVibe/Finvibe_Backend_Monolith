package depth.finvibe.modules.asset.application.event;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRegisteredEvent {
	private Long portfolioId;
	private Long stockId;
	private UUID userId;
	private BigDecimal amount;
	private BigDecimal purchasePriceAmount;
	private String currency;
}
