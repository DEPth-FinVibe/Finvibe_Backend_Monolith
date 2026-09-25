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
    void deleteCurrentPrice(Long stockId);

    List<CurrentPrice> findByStockIds(List<Long> stockIds);

    Map<Long, LocalDateTime> findLastUpdatedAtByStockIds(List<Long> stockIds);
}
