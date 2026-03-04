package depth.finvibe.modules.market.infra.persistence;

import depth.finvibe.modules.market.domain.StockRanking;
import depth.finvibe.modules.market.domain.enums.RankType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRankingJpaRepository extends JpaRepository<StockRanking, Long> {
    List<StockRanking> findByRankTypeOrderByRankAsc(RankType rankType, Pageable pageable);
    
    List<StockRanking> findByRankTypeOrderByRankAsc(RankType rankType);

    void deleteByRankTypeIn(List<RankType> rankTypes);
}
