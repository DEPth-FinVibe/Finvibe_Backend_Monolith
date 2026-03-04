package depth.finvibe.modules.market.infra.persistence;

import java.util.List;

import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.market.domain.Stock;

import static depth.finvibe.modules.market.domain.QStock.stock;

@Repository
public class StockQueryRepository {

  private final JPAQueryFactory queryFactory;

  public StockQueryRepository(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  public List<Stock> searchByNameOrSymbol(String query) {
    String trimmedQuery = query == null ? "" : query.trim();
    if (trimmedQuery.isEmpty()) {
      return List.of();
    }

    NumberExpression<Integer> priority = new CaseBuilder()
        .when(stock.symbol.equalsIgnoreCase(trimmedQuery)).then(1)
        .when(stock.name.equalsIgnoreCase(trimmedQuery)).then(2)
        .when(stock.symbol.startsWithIgnoreCase(trimmedQuery)).then(3)
        .when(stock.name.startsWithIgnoreCase(trimmedQuery)).then(4)
        .when(stock.symbol.containsIgnoreCase(trimmedQuery)).then(5)
        .when(stock.name.containsIgnoreCase(trimmedQuery)).then(6)
        .otherwise(7);

    return queryFactory
        .selectFrom(stock)
        .where(
            stock.name.containsIgnoreCase(trimmedQuery)
                .or(stock.symbol.containsIgnoreCase(trimmedQuery))
        )
        .orderBy(
            priority.asc(),
            stock.name.asc(),
            stock.symbol.asc()
        )
        .fetch();
  }
}
