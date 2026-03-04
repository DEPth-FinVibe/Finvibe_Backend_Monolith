package depth.finvibe.modules.asset.application.port.out;

import java.util.List;

public interface AssetRepository {
    void deleteById(Long assetId);

    void deleteAllById(List<Long> assetIds);
}
