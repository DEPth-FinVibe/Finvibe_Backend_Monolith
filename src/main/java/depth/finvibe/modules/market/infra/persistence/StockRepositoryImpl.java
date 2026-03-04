package depth.finvibe.modules.market.infra.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.market.application.port.out.StockRepository;
import depth.finvibe.modules.market.domain.Stock;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository jpaRepository;
    private final StockQueryRepository stockQueryRepository;

    @Override
    public Optional<Stock> findById(Long stockId) {
        return jpaRepository.findById(stockId);
    }

    @Override
    public Optional<Stock> findBySymbol(String symbol) {
        return jpaRepository.findBySymbol(symbol);
    }

    @Override
    public void save(Stock stock) {
        jpaRepository.save(stock);
    }

    @Override
    public boolean existsById(Long stockId) {
        return jpaRepository.existsById(stockId);
    }

    @Override
    public boolean existsAny() {
        return jpaRepository.count() > 0;
    }

    @Override
    public void bulkUpsertStocks(List<Stock> stocksToUpsert) {
        jpaRepository.saveAll(stocksToUpsert);
    }

    @Override
    public List<Stock> findAllBySymbolIn(List<String> symbols) {
        return jpaRepository.findAllBySymbolIn(symbols);
    }

    @Override
    public List<Stock> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Stock> findAllById(List<Long> stockIds) {
        return jpaRepository.findAllById(stockIds);
    }

    @Override
    public List<Stock> findByCategoryId(Long categoryId) {
        return jpaRepository.findByCategoryId(categoryId);
    }

    @Override
    public int countByCategoryId(Long categoryId) {
        return jpaRepository.countByCategoryId(categoryId);
    }

    @Override
    public List<Long> findAllCategoryStockIds() {
        return jpaRepository.findAllCategoryStockIds();
    }

    @Override
    public List<Long> findAllCategoryStockIdsExcluding(Long excludedCategoryId) {
        return jpaRepository.findAllCategoryStockIdsExcluding(excludedCategoryId);
    }

    @Override
    public List<Stock> searchByNameOrSymbol(String query) {
        return stockQueryRepository.searchByNameOrSymbol(query);
    }
}
