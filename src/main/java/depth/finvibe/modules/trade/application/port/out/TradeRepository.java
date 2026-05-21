package depth.finvibe.modules.trade.application.port.out;

import depth.finvibe.modules.trade.domain.Trade;
import depth.finvibe.modules.trade.domain.enums.TradeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeRepository {

    Trade save(Trade trade);
    Optional<Trade> findById(Long tradeId);

    List<Long> findDistinctStockIdsByUserIdAndTradeType(Long userId, TradeType tradeType);

    List<Trade> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
