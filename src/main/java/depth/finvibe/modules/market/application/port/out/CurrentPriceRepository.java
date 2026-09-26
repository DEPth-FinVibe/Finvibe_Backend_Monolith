package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.CurrentPrice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public interface CurrentPriceRepository {
    /**
     * 저장된 버전보다 체결시각이 이르지 않을 때만 현재가를 저장하고, 부여한 priceVersion을 돌려준다.
     * 더 이른 체결시각이면 아무것도 쓰지 않고 빈 값을 돌려준다.
     */
    OptionalLong saveIfNewer(CurrentPrice currentPrice);

    /**
     * 여러 현재가를 saveIfNewer와 같은 규칙으로 저장한다. 결과는 입력과 같은 순서다.
     * 같은 종목이 여러 번 있으면 앞의 것부터 적용한다.
     */
    default List<OptionalLong> saveAllIfNewer(List<CurrentPrice> currentPrices) {
        return currentPrices.stream().map(this::saveIfNewer).toList();
    }
    void deleteCurrentPrice(Long stockId);

    List<CurrentPrice> findByStockIds(List<Long> stockIds);

    Map<Long, LocalDateTime> findLastUpdatedAtByStockIds(List<Long> stockIds);
}
