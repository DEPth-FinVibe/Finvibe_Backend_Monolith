package depth.finvibe.modules.market.application.port.out;

import depth.finvibe.modules.market.domain.StockRanking;
import depth.finvibe.modules.market.domain.enums.RankType;

import java.util.List;

public interface StockRankingRepository {
    void bulkUpsertStockRankings(List<StockRanking> stockRankings);

    List<StockRanking> findByRankType(RankType rankType);

    void deleteByRankTypeIn(List<RankType> rankTypes);
}
