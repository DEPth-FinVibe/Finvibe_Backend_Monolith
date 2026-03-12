package depth.finvibe.modules.trade.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.trade.application.port.in.TradeCommandUseCase;
import depth.finvibe.modules.trade.application.port.in.TradeEventUseCase;
import depth.finvibe.modules.trade.domain.enums.TradeType;
import depth.finvibe.modules.trade.domain.error.TradeErrorCode;
import depth.finvibe.modules.trade.dto.TradeDto;
import depth.finvibe.common.investment.dto.ReservationSatisfiedEvent;
import depth.finvibe.common.error.DomainException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeEventService implements TradeEventUseCase {

    private final TradeCommandUseCase tradeCommandUseCase;

    @Transactional
    public void processReservedTradeExecution(ReservationSatisfiedEvent event) {

        try {
            Long tradeId = event.getTradeId();

            TradeDto.TradeResponse response = tradeCommandUseCase.executeReservedTrade(tradeId);

            if (response.getTradeType() == TradeType.FAILED) {
                log.warn("예약 거래 체결 실패(체결 조건 부족): tradeId={}, amount={}, price={}",
                        response.getTradeId(),
                        response.getAmount(),
                        response.getPrice());
                return;
            }

            log.info("예약 거래 체결 완료: tradeId={}, amount={}, price={}",
                response.getTradeId(),
                response.getAmount(),
                response.getPrice());

        } catch (NumberFormatException e) {
            log.error("잘못된 tradeId 형식: tradeId={}", event.getTradeId(), e);
            throw new DomainException(TradeErrorCode.INVALID_TRADE_ID_FORMAT);
        } catch (Exception e) {
            log.error("예약 거래 체결 중 오류 발생: tradeId={}", event.getTradeId(), e);
            throw e;
        }
    }
}
