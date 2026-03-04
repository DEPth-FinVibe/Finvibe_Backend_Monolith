package depth.finvibe.modules.market.application;

import depth.finvibe.modules.market.application.port.in.ReservationQueryUseCase;
import depth.finvibe.modules.market.application.port.out.ReservationEventPublisher;
import depth.finvibe.modules.market.application.port.out.ReservationRepository;
import depth.finvibe.modules.market.domain.Reservation;
import depth.finvibe.modules.market.domain.enums.ReservationType;
import depth.finvibe.common.investment.dto.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationQueryService implements ReservationQueryUseCase {

    private final ReservationRepository reservationRepository;
    private final ReservationEventPublisher reservationEventPublisher;

    @Override
    public void makeReservation(TradeExecutedEvent event) {
        ReservationType type;
        if(event.getType().equals("BUY")) {
            type = ReservationType.BUY;
        } else if(event.getType().equals("SELL")) {
            type = ReservationType.SELL;
        } else {
            throw new IllegalArgumentException("Invalid trade type: " + event.getType());
        }

        Reservation reservation = Reservation.create(
                event.getTradeId(),
                event.getStockId(),
                event.getPrice(),
                type,
                LocalDateTime.now()
        );
        reservationRepository.save(reservation);
    }

    @Override
    public void cancelReservation(Long tradeId) {
        reservationRepository.deleteByTradeId(tradeId);
    }

    @Override
    public void reservedStockPriceChanged(Long stockId, Long newPrice) {
        List<Reservation> lowerPriceStocks = reservationRepository.findBuyConditionMet(stockId, newPrice);
        List<Reservation> higherPriceStocks = reservationRepository.findSellConditionMet(stockId, newPrice);

        List<Reservation> allStocks = new ArrayList<>();
        allStocks.addAll(lowerPriceStocks);
        allStocks.addAll(higherPriceStocks);

        for(Reservation reservation : allStocks) {
            reservationEventPublisher.publishReservationConditionMetEvent(
                    reservation.getTradeId(),
                    reservation.getType(),
                    reservation.getStockId(),
                    newPrice
            );
        }

        for(Reservation reservation : allStocks) {
            reservationRepository.deleteByTradeId(reservation.getTradeId());
        }
    }
}
