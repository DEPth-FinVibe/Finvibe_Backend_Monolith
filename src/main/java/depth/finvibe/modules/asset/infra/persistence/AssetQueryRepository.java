package depth.finvibe.modules.asset.infra.persistence;

import java.math.BigDecimal;
import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static depth.finvibe.modules.asset.domain.QAsset.asset;

@Repository
@RequiredArgsConstructor
public class AssetQueryRepository {

	private final JPAQueryFactory queryFactory;

	public List<Long> findDistinctHoldingStockIds() {
		return queryFactory
				.select(asset.stockId)
				.from(asset)
				.where(
						asset.stockId.isNotNull(),
						asset.amount.gt(BigDecimal.ZERO)
				)
				.distinct()
				.fetch();
	}
}
