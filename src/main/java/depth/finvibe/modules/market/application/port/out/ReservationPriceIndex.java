package depth.finvibe.modules.market.application.port.out;

/**
 * 종목별 예약 목표가 경계(최고 매수 목표가, 최저 매도 목표가)를 메모리에 두고, 틱마다 예약 조회가 필요한지 판단한다.
 */
public interface ReservationPriceIndex {

    /**
     * 이 가격에서 조건을 만족하는 예약이 있을 수 있으면 true다. 예약이 없다고 확실할 때만 false를 돌려준다.
     */
    boolean mayTrigger(Long stockId, long price);
}
