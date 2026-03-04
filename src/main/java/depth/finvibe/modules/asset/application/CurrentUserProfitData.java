package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CurrentUserProfitData(
  UUID userId,
  String userNickname,
  BigDecimal totalCurrentValue,
  BigDecimal totalProfitLoss,
  BigDecimal totalReturnRate
) {
}
