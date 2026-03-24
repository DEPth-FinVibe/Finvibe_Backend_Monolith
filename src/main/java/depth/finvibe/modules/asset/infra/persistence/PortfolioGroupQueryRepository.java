package depth.finvibe.modules.asset.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.UserProfitSummaryRow;
import depth.finvibe.modules.asset.domain.PortfolioGroup;
import depth.finvibe.modules.asset.dto.TopHoldingStockDto;

import static depth.finvibe.modules.asset.domain.QAsset.asset;
import static depth.finvibe.modules.asset.domain.QPortfolioGroup.portfolioGroup;

@Repository
@RequiredArgsConstructor
public class PortfolioGroupQueryRepository {
    private final JPAQueryFactory queryFactory;

    public Optional<PortfolioGroup> findByIdWithAssets(Long id) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(portfolioGroup)
                        .leftJoin(portfolioGroup.assets, asset).fetchJoin()
                        .where(portfolioGroup.id.eq(id))
                        .fetchOne()
        );
    }

    public Optional<PortfolioGroup> findDefaultByUserId(UUID userId) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(portfolioGroup)
                        .leftJoin(portfolioGroup.assets, asset).fetchJoin()
                        .where(portfolioGroup.userId.eq(userId).and(portfolioGroup.isDefault.eq(true)))
                        .fetchOne()
        );
    }

    public List<PortfolioGroup> findAllWithAssets() {
        return queryFactory
                .selectFrom(portfolioGroup)
                .leftJoin(portfolioGroup.assets, asset).fetchJoin()
                .distinct()
                .fetch();
    }

    public List<PortfolioGroup> findAllByUserIdWithAssets(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return queryFactory
                .selectFrom(portfolioGroup)
                .leftJoin(portfolioGroup.assets, asset).fetchJoin()
                .where(portfolioGroup.userId.eq(userId))
                .distinct()
                .fetch();
    }

    public List<PortfolioGroup> findAllByStockIdsWithAssets(List<Long> stockIds) {
        return queryFactory
                .selectFrom(portfolioGroup)
                .leftJoin(portfolioGroup.assets, asset).fetchJoin()
                .where(asset.stockId.in(stockIds))
                .distinct()
                .fetch();
    }

    public List<UserProfitSummaryRow> findAllUserProfitSummaries() {
        return queryFactory
                .select(Projections.constructor(
                        UserProfitSummaryRow.class,
                        portfolioGroup.userId,
                        portfolioGroup.valuation.totalCurrentValue.sum(),
                        portfolioGroup.valuation.totalProfitLoss.sum()
                ))
                .from(portfolioGroup)
                .groupBy(portfolioGroup.userId)
                .fetch();
    }

    public List<UUID> findUserIdsWithAssets() {
        return queryFactory
                .select(portfolioGroup.userId)
                .from(asset)
                .join(asset.portfolioGroup, portfolioGroup)
                .distinct()
                .fetch();
    }

    public List<TopHoldingStockDto.TopHoldingStockResponse> findTopHoldingStocks(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return queryFactory
                .select(Projections.constructor(
                        TopHoldingStockDto.TopHoldingStockResponse.class,
                        asset.stockId,
                        asset.name,
                        asset.amount.sum()
                ))
                .from(asset)
                .groupBy(asset.stockId, asset.name)
                .orderBy(asset.amount.sum().desc(), asset.stockId.asc())
                .limit(limit)
                .fetch();
    }

    public boolean existDefaultByUserId(UUID userId) {
        return queryFactory
                .selectFrom(portfolioGroup)
                .where(portfolioGroup.userId.eq(userId).and(portfolioGroup.isDefault.eq(true)))
                .fetchFirst() != null;
    }
}
