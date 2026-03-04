package depth.finvibe.modules.study.application.port.out;


import depth.finvibe.common.gamification.dto.TradeDto;

import java.time.LocalDate;
import java.util.List;

public interface TradeServiceClient {
    List<TradeDto.TradeHistoryResponse> getUserTradeHistories(
            String userId,
            LocalDate fromDate,
            LocalDate toDate
    );
}
