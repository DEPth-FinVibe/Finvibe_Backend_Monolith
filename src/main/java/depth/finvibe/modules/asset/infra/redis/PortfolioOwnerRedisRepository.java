package depth.finvibe.modules.asset.infra.redis;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포트폴리오 → 소유자 매핑.
 *
 * Key: portfolio:owner:{portfolioId}
 * Type: STRING
 * Value: userId (UUID)
 */
@Repository
@RequiredArgsConstructor
public class PortfolioOwnerRedisRepository {

	private static final String KEY_PREFIX = "portfolio:owner:";

	private final StringRedisTemplate redisTemplate;

	public void set(Long portfolioId, UUID userId) {
		redisTemplate.opsForValue().set(key(portfolioId), userId.toString());
	}

	public UUID get(Long portfolioId) {
		String value = redisTemplate.opsForValue().get(key(portfolioId));
		if (value == null) {
			return null;
		}
		return UUID.fromString(value);
	}

	public void delete(Long portfolioId) {
		redisTemplate.delete(key(portfolioId));
	}

	private String key(Long portfolioId) {
		return KEY_PREFIX + portfolioId;
	}
}
