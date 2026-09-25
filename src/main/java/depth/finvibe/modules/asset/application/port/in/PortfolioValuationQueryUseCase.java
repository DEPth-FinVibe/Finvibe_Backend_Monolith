package depth.finvibe.modules.asset.application.port.in;

import depth.finvibe.modules.asset.dto.PortfolioValuationDto;

public interface PortfolioValuationQueryUseCase {
    PortfolioValuationDto.ValuationsResponse getValuations(Long userId);
}
