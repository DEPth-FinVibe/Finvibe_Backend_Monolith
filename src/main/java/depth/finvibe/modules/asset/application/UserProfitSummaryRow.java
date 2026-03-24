package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.util.UUID;

public record UserProfitSummaryRow(
	UUID userId,
	BigDecimal totalCurrentValue,
	BigDecimal totalProfitLoss
) {
}
