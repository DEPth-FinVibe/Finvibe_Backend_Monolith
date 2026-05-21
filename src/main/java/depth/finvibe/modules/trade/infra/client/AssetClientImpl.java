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
    public boolean isExistPortfolio(Long portfolioId, Long userId) {
        return assetQueryUseCase.isExistPortfolio(portfolioId, userId);
    }

    @Override
    public boolean hasSufficientStockAmount(Long portfolioId, Long userId, Long stockId, Double amount) {
        return assetQueryUseCase.hasSufficientStockAmount(portfolioId, userId, stockId, amount);
    }
}
