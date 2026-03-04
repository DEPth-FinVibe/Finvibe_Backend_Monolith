package depth.finvibe.common.investment.dto;

import java.util.List;

public record UserLoginedEvent(
    String userId,
    List<Long> interestedStockIds,
    List<Long> ownedStockIds
) {
}
