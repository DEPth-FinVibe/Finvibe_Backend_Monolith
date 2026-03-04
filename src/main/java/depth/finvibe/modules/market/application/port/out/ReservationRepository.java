package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.Reservation;

import java.util.List;

public interface ReservationRepository {
    void save(Reservation reservation);
    void deleteByTradeId(Long tradeId);
    void clear();

    List<Long> findReservedStockIds();
    List<Reservation> findBuyConditionMet(Long stockId, Long price);
    List<Reservation> findSellConditionMet(Long stockId, Long price);
}
