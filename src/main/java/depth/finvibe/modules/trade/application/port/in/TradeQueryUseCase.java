package depth.finvibe.modules.trade.application.port.in;

import depth.finvibe.modules.trade.dto.TradeDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TradeQueryUseCase {
    TradeDto.TradeResponse findTrade(Long tradeId);

    List<Long> findReservedStockIds(UUID userId);

    List<TradeDto.TradeHistoryResponse> findTradesByMonth(UUID userId, int year, int month);

    List<TradeDto.TradeHistoryResponse> findTradesByDateRange(UUID userId, LocalDate fromDate, LocalDate toDate);
}
