package depth.finvibe.modules.user.infra.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.user.application.port.out.TokenFamilyCacheRepository;
import depth.finvibe.modules.user.domain.TokenFamily;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TokenFamilyCacheRepositoryImpl implements TokenFamilyCacheRepository {

	private static final String STATUS_FIELD = "status";
	private static final String EXPIRES_AT_FIELD = "expiresAt";

	private final StringRedisTemplate redisTemplate;

	@Value("${auth.token-family.redis-key-prefix:auth:family:}")
	private String keyPrefix;

	@Override
	public void save(TokenFamily tokenFamily) {
		String key = keyPrefix + tokenFamily.getId();
		redisTemplate.opsForHash().putAll(key, Map.of(
			STATUS_FIELD, tokenFamily.getStatus().name(),
			EXPIRES_AT_FIELD, tokenFamily.getExpiresAt().toString()
		));

		Duration ttl = resolveTtl(tokenFamily.getExpiresAt());
		if (!ttl.isNegative() && !ttl.isZero()) {
			redisTemplate.expire(key, ttl);
		}
	}

	private Duration resolveTtl(Instant expiresAt) {
		Duration ttl = Duration.between(Instant.now(), expiresAt.plus(Duration.ofDays(1)));
		if (ttl.isNegative() || ttl.isZero()) {
			return Duration.ofHours(1);
		}
		return ttl;
	}
}
