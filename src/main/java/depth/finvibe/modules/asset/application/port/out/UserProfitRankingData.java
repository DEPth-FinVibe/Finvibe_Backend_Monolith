package depth.finvibe.modules.asset.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

public record UserProfitRankingData(
    UUID userId,
    String userNickname,
    BigDecimal totalReturnRate,
    BigDecimal totalProfitLoss
) {}
