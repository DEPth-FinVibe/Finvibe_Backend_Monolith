package depth.finvibe.modules.asset.infra.redis;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.application.port.out.HoldingStockProjectionRepository;

/**
 * 종목 → 포트폴리오 역방향 인덱스.
 * 특정 종목을 보유한 포트폴리오 ID 목록을 Redis SET으로 관리한다.
 *
 * Key: stock:{stockId}:portfolios
 * Type: SET
 * Members: portfolioId (Long → String)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StockHoldingIndexRedisRepository implements HoldingStockProjectionRepository {

	private static final String KEY_PREFIX = "stock:";
	private static final String KEY_SUFFIX = ":portfolios";
	private static final String MARKET_HOLDING_STOCK_IDS_KEY = "market:holding:stock-ids";
	private static final String MARKET_HOLDING_USER_KEY_PREFIX = "market:holding:stock:";
	private static final String MARKET_HOLDING_USER_KEY_SUFFIX = ":users";

	private final StringRedisTemplate redisTemplate;

	public void addPortfolio(Long stockId, Long portfolioId) {
		redisTemplate.opsForSet().add(key(stockId), portfolioId.toString());
		redisTemplate.opsForSet().add(MARKET_HOLDING_STOCK_IDS_KEY, stockId.toString());
	}

	public void removePortfolio(Long stockId, Long portfolioId) {
		String portfolioKey = key(stockId);
		redisTemplate.opsForSet().remove(portfolioKey, portfolioId.toString());
		deleteIfEmpty(portfolioKey);
		removeGlobalStockIdIfUnused(stockId);
	}

	public Set<Long> getPortfolioIds(Long stockId) {
		Set<String> members = redisTemplate.opsForSet().members(key(stockId));
		if (members == null || members.isEmpty()) {
			return Collections.emptySet();
		}
		return members.stream()
				.map(Long::valueOf)
				.collect(Collectors.toSet());
	}

	public void replacePortfolios(Long stockId, Set<Long> portfolioIds) {
		String k = key(stockId);
		redisTemplate.delete(k);
		if (portfolioIds != null && !portfolioIds.isEmpty()) {
			String[] members = portfolioIds.stream()
					.map(String::valueOf)
					.toArray(String[]::new);
			redisTemplate.opsForSet().add(k, members);
			redisTemplate.opsForSet().add(MARKET_HOLDING_STOCK_IDS_KEY, stockId.toString());
		} else {
			removeGlobalStockIdIfUnused(stockId);
		}
	}

	@Override
	public void replaceHoldingStockIds(Set<Long> stockIds) {
		redisTemplate.delete(MARKET_HOLDING_STOCK_IDS_KEY);
		if (stockIds == null || stockIds.isEmpty()) {
			return;
		}

		String[] members = stockIds.stream()
				.map(String::valueOf)
				.toArray(String[]::new);
		redisTemplate.opsForSet().add(MARKET_HOLDING_STOCK_IDS_KEY, members);
	}

	@Override
	public boolean isEmpty() {
		Long size = redisTemplate.opsForSet().size(MARKET_HOLDING_STOCK_IDS_KEY);
		return size == null || size == 0L;
	}

	public boolean exists(Long stockId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key(stockId)));
	}

	private String key(Long stockId) {
		return KEY_PREFIX + stockId + KEY_SUFFIX;
	}

	private String userKey(Long stockId) {
		return MARKET_HOLDING_USER_KEY_PREFIX + stockId + MARKET_HOLDING_USER_KEY_SUFFIX;
	}

	private void deleteIfEmpty(String key) {
		Long size = redisTemplate.opsForSet().size(key);
		if (size != null && size == 0L) {
			redisTemplate.delete(key);
		}
	}

	private void removeGlobalStockIdIfUnused(Long stockId) {
		if (!hasMembers(key(stockId)) && !hasMembers(userKey(stockId))) {
			redisTemplate.opsForSet().remove(MARKET_HOLDING_STOCK_IDS_KEY, stockId.toString());
		}
	}

	private boolean hasMembers(String key) {
		Long size = redisTemplate.opsForSet().size(key);
		return size != null && size > 0L;
	}
}
