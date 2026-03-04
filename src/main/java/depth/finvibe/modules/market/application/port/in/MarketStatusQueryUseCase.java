package depth.finvibe.modules.market.application.port.in;

import depth.finvibe.modules.market.dto.MarketStatusDto;

public interface MarketStatusQueryUseCase {

  MarketStatusDto.Response getMarketStatus();
}
