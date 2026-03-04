package depth.finvibe.modules.asset.application.port.out;

import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

public interface TopHoldingStockCacheRepository {
    Optional<TopHoldingStockDto.TopHoldingStockListResponse> find(UUID userId);

    void save(UUID userId, TopHoldingStockDto.TopHoldingStockListResponse response);

    void evictByUserId(UUID userId);
}
