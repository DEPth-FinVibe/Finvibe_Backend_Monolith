package depth.finvibe.modules.asset.infra.redis;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioStateRedisRepository {

	private static final String OWNER_FIELD = "u";

	private final StringRedisTemplate redisTemplate;

	public void setOwner(Long portfolioId, Long userId) {
		redisTemplate.opsForHash().put(key(portfolioId), OWNER_FIELD, userId.toString());
	}

	public void deleteOwner(Long portfolioId) {
		redisTemplate.opsForHash().delete(key(portfolioId), OWNER_FIELD);
	}

	private String key(Long portfolioId) {
		return "pf:" + portfolioId;
	}
}
