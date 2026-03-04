package depth.finvibe.common.investment.dto;

import java.util.List;

public record UserLogoutedEvent (
    String userId,
    List<Long> interestedStockIds,
    List<Long> ownedStockIds
){}