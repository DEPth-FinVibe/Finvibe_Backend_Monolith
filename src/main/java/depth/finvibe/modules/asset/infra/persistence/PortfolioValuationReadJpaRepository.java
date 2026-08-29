package depth.finvibe.modules.asset.infra.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PortfolioValuationReadJpaRepository extends Repository<PortfolioValuationReadEntity, Long> {
    @Query("""
        select valuation
        from PortfolioValuationReadEntity valuation
        where valuation.portfolioId in :portfolioIds
          and valuation.deleted = false
        """)
    List<PortfolioValuationReadEntity> findActiveByPortfolioIds(
        @Param("portfolioIds") Collection<Long> portfolioIds
    );
}
