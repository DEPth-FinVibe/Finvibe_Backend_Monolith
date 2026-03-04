package depth.finvibe.modules.market.infra.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import depth.finvibe.modules.market.domain.Stock;

public interface StockJpaRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findAllBySymbolIn(List<String> symbols);

    List<Stock> findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(String nameQuery, String symbolQuery);

    List<Stock> findByCategoryId(Long categoryId);

    int countByCategoryId(Long categoryId);

    @Query("select s.id from Stock s where s.categoryId is not null")
    List<Long> findAllCategoryStockIds();

    @Query("select s.id from Stock s where s.categoryId is not null and s.categoryId <> :excludedCategoryId")
    List<Long> findAllCategoryStockIdsExcluding(@Param("excludedCategoryId") Long excludedCategoryId);
}
