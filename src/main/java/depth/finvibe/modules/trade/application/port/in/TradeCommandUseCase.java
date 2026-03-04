package depth.finvibe.modules.trade.application.port.in;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.trade.dto.TradeDto;

public interface TradeCommandUseCase {

    TradeDto.TradeResponse createTrade(TradeDto.TransactionRequest request, Requester requester);

    TradeDto.TradeResponse cancelTrade(Long tradeId, Requester requester);

    TradeDto.TradeResponse executeReservedTrade(Long tradeId);
}

