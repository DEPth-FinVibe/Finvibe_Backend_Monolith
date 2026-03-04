package depth.finvibe.modules.trade.infra.client;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.asset.application.port.in.AssetQueryUseCase;
import depth.finvibe.modules.trade.application.port.out.AssetClient;

@Component
@RequiredArgsConstructor
public class AssetClientImpl implements AssetClient {
    private final AssetQueryUseCase assetQueryUseCase;

    @Override
    public boolean isExistPortfolio(Long portfolioId, UUID userId) {
        return assetQueryUseCase.isExistPortfolio(portfolioId, userId);
    }

    @Override
    public boolean hasSufficientStockAmount(Long portfolioId, UUID userId, Long stockId, Double amount) {
        return assetQueryUseCase.hasSufficientStockAmount(portfolioId, userId, stockId, amount);
    }
}
