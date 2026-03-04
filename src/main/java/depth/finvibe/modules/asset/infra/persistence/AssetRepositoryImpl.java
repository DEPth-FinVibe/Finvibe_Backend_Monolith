package depth.finvibe.modules.asset.infra.persistence;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.AssetRepository;

@Repository
@RequiredArgsConstructor
public class AssetRepositoryImpl implements AssetRepository {
    private final AssetJpaRepository jpaRepository;

    @Override
    public void deleteById(Long assetId) {
        jpaRepository.deleteById(assetId);
    }

    @Override
    public void deleteAllById(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        jpaRepository.deleteAllById(assetIds);
    }
}
