package depth.finvibe.modules.market.infra.redis;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.market.application.port.out.HoldingStockRepository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class HoldingStockRedisRepository implements HoldingStockRepository {

	private static final String HOLDING_STOCK_IDS_KEY = "market:holding:stock-ids";
	private static final String HOLDING_USER_KEY_PREFIX = "market:holding:stock:";
	private static final String HOLDING_USER_KEY_SUFFIX = ":users";
	private static final String PORTFOLIO_KEY_PREFIX = "stock:";
	private static final String PORTFOLIO_KEY_SUFFIX = ":portfolios";

	private final StringRedisTemplate redisTemplate;

	@Override
	public void registerHoldingStock(Long stockId, Long userId) {
		redisTemplate.opsForSet().add(userKey(stockId), userId.toString());
		redisTemplate.opsForSet().add(HOLDING_STOCK_IDS_KEY, stockId.toString());
	}

	@Override
	public void unregisterHoldingStock(Long stockId, Long userId) {
		String userKey = userKey(stockId);
		redisTemplate.opsForSet().remove(userKey, userId.toString());
		deleteIfEmpty(userKey);

		if (!hasMembers(userKey) && !hasMembers(portfolioKey(stockId))) {
			redisTemplate.opsForSet().remove(HOLDING_STOCK_IDS_KEY, stockId.toString());
		}
	}

	@Override
	public List<Long> findAllDistinctStockIds() {
		Set<String> members = redisTemplate.opsForSet().members(HOLDING_STOCK_IDS_KEY);
		if (members == null || members.isEmpty()) {
			return Collections.emptyList();
		}

		return members.stream()
				.map(this::parseStockId)
				.flatMap(java.util.Optional::stream)
				.toList();
	}

	private java.util.Optional<Long> parseStockId(String raw) {
		try {
			return java.util.Optional.of(Long.valueOf(raw));
		} catch (NumberFormatException ex) {
			log.warn("Invalid holding stock id in Redis projection: {}", raw);
			return java.util.Optional.empty();
		}
	}

	private String userKey(Long stockId) {
		return HOLDING_USER_KEY_PREFIX + stockId + HOLDING_USER_KEY_SUFFIX;
	}

	private String portfolioKey(Long stockId) {
		return PORTFOLIO_KEY_PREFIX + stockId + PORTFOLIO_KEY_SUFFIX;
	}

	private void deleteIfEmpty(String key) {
		Long size = redisTemplate.opsForSet().size(key);
		if (size != null && size == 0L) {
			redisTemplate.delete(key);
		}
	}

	private boolean hasMembers(String key) {
		Long size = redisTemplate.opsForSet().size(key);
		return size != null && size > 0L;
	}
}
