package depth.finvibe.modules.market.infra.persistence;

import depth.finvibe.modules.market.application.port.out.StockRankingRepository;
import depth.finvibe.modules.market.domain.StockRanking;
import depth.finvibe.modules.market.domain.enums.RankType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StockRankingRepositoryImpl implements StockRankingRepository {

    private final StockRankingJpaRepository jpaRepository;

    @Override
    @Transactional
    public void bulkUpsertStockRankings(List<StockRanking> stockRankings) {
        jpaRepository.saveAll(stockRankings);
    }

    @Override
    public List<StockRanking> findByRankType(RankType rankType) {
        return jpaRepository.findByRankTypeOrderByRankAsc(rankType);
    }

    @Override
    @Transactional
    public void deleteByRankTypeIn(List<RankType> rankTypes) {
        jpaRepository.deleteByRankTypeIn(rankTypes);
        jpaRepository.flush();
    }
}
