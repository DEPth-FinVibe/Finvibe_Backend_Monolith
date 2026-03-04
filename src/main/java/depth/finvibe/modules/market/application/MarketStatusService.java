package depth.finvibe.modules.market.application;

import org.springframework.stereotype.Service;

import depth.finvibe.modules.market.application.port.in.MarketStatusQueryUseCase;
import depth.finvibe.modules.market.domain.MarketHours;
import depth.finvibe.modules.market.dto.MarketStatusDto;

@Service
public class MarketStatusService implements MarketStatusQueryUseCase {

    @Override
    public MarketStatusDto.Response getMarketStatus() {
        return MarketStatusDto.Response.from(MarketHours.getCurrentStatus());
    }
}
