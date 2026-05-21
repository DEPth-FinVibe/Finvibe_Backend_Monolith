package depth.finvibe.modules.asset.application.port.out;

import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

public interface TopHoldingStockCacheRepository {
    Optional<TopHoldingStockDto.TopHoldingStockListResponse> find(Long userId);

    void save(Long userId, TopHoldingStockDto.TopHoldingStockListResponse response);

    void evictByUserId(Long userId);
}
