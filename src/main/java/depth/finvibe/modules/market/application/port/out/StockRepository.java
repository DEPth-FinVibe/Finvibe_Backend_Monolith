package depth.finvibe.modules.market.application.port.out;

import java.util.List;
import java.util.Optional;

import depth.finvibe.modules.market.domain.Stock;

public interface StockRepository {
    Optional<Stock> findById(Long stockId);

    Optional<Stock> findBySymbol(String symbol);

    void save(Stock stock);

    boolean existsById(Long stockId);

    boolean existsAny();

    void bulkUpsertStocks(List<Stock> stocksToUpsert);

    List<Stock> findAllBySymbolIn(List<String> symbols);

    List<Stock> findAll();

    List<Stock> findAllById(List<Long> stockIds);

    List<Stock> findByCategoryId(Long categoryId);

    int countByCategoryId(Long categoryId);

    List<Long> findAllCategoryStockIds();

    List<Long> findAllCategoryStockIdsExcluding(Long excludedCategoryId);

    List<Stock> searchByNameOrSymbol(String query);
}
