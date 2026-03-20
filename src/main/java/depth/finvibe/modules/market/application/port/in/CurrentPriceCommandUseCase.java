package depth.finvibe.modules.market.application.port.in;

import java.util.UUID;

import depth.finvibe.modules.market.dto.CurrentPriceUpdatedEvent;

public interface CurrentPriceCommandUseCase {
    //유저가 보고있는 종목 등록 (실시간 종목 카운트 증가)
    void registerWatchingStock(Long stockId, UUID userId);

    // 활성 구독의 watcher TTL 연장
    void renewWatchingStock(Long stockId, UUID userId);

    //유저가 보고있는 종목 해제 (실시간 종목 카운트 감소)ø
    void unregisterWatchingStock(Long stockId, UUID userId);

    // 유저가 보유한 종목 등록 (준실시간 종목 카운트 증가)
    void registerHoldingStock(Long stockId, UUID userId);

    // 유저가 보유한 종목 해제 (준실시간 종목 카운트 감소)
    void unregisterHoldingStock(Long stockId, UUID userId);

    // 실시간 주가 업데이트 처리
    // infra 계층은 redis에 저장된 인덱스를 보고 실시간으로 업데이트 하여 업데이트되면 이 메서드를 호출한다.
    void stockPriceUpdated(CurrentPriceUpdatedEvent priceUpdate);
}
