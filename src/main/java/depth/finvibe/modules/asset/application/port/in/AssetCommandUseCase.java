package depth.finvibe.modules.asset.application.port.in;

import java.util.UUID;

import depth.finvibe.modules.asset.dto.PortfolioGroupDto;

public interface AssetCommandUseCase {
    void registerAsset(Long portfolioId, PortfolioGroupDto.RegisterAssetRequest request, Long requesterUserId);

    void unregisterAsset(Long portfolioId, PortfolioGroupDto.UnregisterAssetRequest request, Long requesterUserId);

    void transferAsset(Long sourcePortfolioId, Long assetId, PortfolioGroupDto.TransferAssetRequest request, Long requesterUserId);

    void createPortfolioGroup(PortfolioGroupDto.CreatePortfolioGroupRequest request, Long requesterUserId);

    void updatePortfolioGroup(Long portfolioGroupId, PortfolioGroupDto.UpdatePortfolioGroupRequest request, Long requesterUserId);

    void deletePortfolioGroup(Long portfolioGroupId, Long requesterUserId);
    
    void createDefaultPortfolioGroup(Long targetUserId);
}
