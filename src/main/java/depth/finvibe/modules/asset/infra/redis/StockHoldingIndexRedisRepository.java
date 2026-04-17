package depth.finvibe.modules.asset.infra.redis;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 종목 → 포트폴리오 역방향 인덱스.
 * 특정 종목을 보유한 포트폴리오 ID 목록을 Redis SET으로 관리한다.
 *
 * Key: stock:holding:{stockId}:portfolios
 * Type: SET
 * Members: portfolioId (Long → String)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StockHoldingIndexRedisRepository {

	private static final String KEY_PREFIX = "stock:holding:";
	private static final String KEY_SUFFIX = ":portfolios";

	private final StringRedisTemplate redisTemplate;

	public void addPortfolio(Long stockId, Long portfolioId) {
		redisTemplate.opsForSet().add(key(stockId), portfolioId.toString());
	}

	public void removePortfolio(Long stockId, Long portfolioId) {
		redisTemplate.opsForSet().remove(key(stockId), portfolioId.toString());
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
		}
	}

	public boolean exists(Long stockId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key(stockId)));
	}

	private String key(Long stockId) {
		return KEY_PREFIX + stockId + KEY_SUFFIX;
	}
}
