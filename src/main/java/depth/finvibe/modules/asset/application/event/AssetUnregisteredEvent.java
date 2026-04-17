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
public class AssetUnregisteredEvent {
	private Long portfolioId;
	private Long stockId;
	private UUID userId;
	private BigDecimal remainingAmount;
	private BigDecimal remainingPurchasePriceAmount;
	private String currency;
	private boolean fullyRemoved;
}
