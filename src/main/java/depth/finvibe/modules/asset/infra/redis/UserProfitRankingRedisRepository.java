package depth.finvibe.modules.asset.infra.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;

/**
 * 유저 수익률 랭킹 (Redis Sorted Set).
 *
 * Key: user:profit-ranking:{rankType}
 * Type: ZSET
 * Score: returnRate (BigDecimal → double)
 * Member: userId (UUID → String)
 */
@Repository
@RequiredArgsConstructor
public class UserProfitRankingRedisRepository {

	private static final String KEY_PREFIX = "user:profit-ranking:";

	private final StringRedisTemplate redisTemplate;

	public void updateScore(UserProfitRankType rankType, UUID userId, double returnRate) {
		redisTemplate.opsForZSet().add(key(rankType), userId.toString(), returnRate);
	}

	public void removeUser(UserProfitRankType rankType, UUID userId) {
		redisTemplate.opsForZSet().remove(key(rankType), userId.toString());
	}

	public List<RankingEntry> getTopN(UserProfitRankType rankType, int n) {
		Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
				.reverseRangeWithScores(key(rankType), 0, (long) n - 1);
		if (tuples == null || tuples.isEmpty()) {
			return Collections.emptyList();
		}

		List<RankingEntry> result = new ArrayList<>(tuples.size());
		int rank = 1;
		for (TypedTuple<String> tuple : tuples) {
			result.add(new RankingEntry(
					UUID.fromString(tuple.getValue()),
					rank++,
					tuple.getScore() != null ? tuple.getScore() : 0.0
			));
		}
		return result;
	}

	public Long getRank(UserProfitRankType rankType, UUID userId) {
		Long rank = redisTemplate.opsForZSet().reverseRank(key(rankType), userId.toString());
		return rank != null ? rank + 1 : null;
	}

	public long getCount(UserProfitRankType rankType) {
		Long size = redisTemplate.opsForZSet().zCard(key(rankType));
		return size != null ? size : 0L;
	}

	public void replaceAll(UserProfitRankType rankType, List<RankingEntry> entries) {
		String k = key(rankType);
		redisTemplate.delete(k);
		if (entries != null && !entries.isEmpty()) {
			for (RankingEntry entry : entries) {
				redisTemplate.opsForZSet().add(k, entry.userId().toString(), entry.returnRate());
			}
		}
	}

	private String key(UserProfitRankType rankType) {
		return KEY_PREFIX + rankType.name().toLowerCase();
	}

	public record RankingEntry(UUID userId, int rank, double returnRate) {
	}
}
