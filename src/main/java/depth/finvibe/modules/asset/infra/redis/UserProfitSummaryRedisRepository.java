package depth.finvibe.modules.asset.infra.redis;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 유저별 수익률 요약 (API 응답용 캐시).
 *
 * Key: user:profit-summary:{userId}
 * Type: HASH
 * Fields: totalCurrentValue, totalProfitLoss, returnRate, calculatedAt
 */
@Repository
@RequiredArgsConstructor
public class UserProfitSummaryRedisRepository {

	private static final String KEY_PREFIX = "user:profit-summary:";

	private static final String FIELD_TOTAL_CURRENT_VALUE = "tcv";
	private static final String FIELD_TOTAL_PROFIT_LOSS = "tpl";
	private static final String FIELD_RETURN_RATE = "rr";
	private static final String FIELD_CALCULATED_AT = "at";

	private final StringRedisTemplate redisTemplate;

	public void update(UUID userId, BigDecimal totalCurrentValue, BigDecimal totalProfitLoss, BigDecimal returnRate) {
		Map<String, String> fields = Map.of(
				FIELD_TOTAL_CURRENT_VALUE, totalCurrentValue.toPlainString(),
				FIELD_TOTAL_PROFIT_LOSS, totalProfitLoss.toPlainString(),
				FIELD_RETURN_RATE, returnRate.toPlainString(),
				FIELD_CALCULATED_AT, LocalDateTime.now().toString()
		);
		redisTemplate.opsForHash().putAll(key(userId), fields);
	}

	public ProfitSummary get(UUID userId) {
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(userId));
		if (entries.isEmpty()) {
			return null;
		}

		return new ProfitSummary(
				new BigDecimal(entries.get(FIELD_TOTAL_CURRENT_VALUE).toString()),
				new BigDecimal(entries.get(FIELD_TOTAL_PROFIT_LOSS).toString()),
				new BigDecimal(entries.get(FIELD_RETURN_RATE).toString()),
				LocalDateTime.parse(entries.get(FIELD_CALCULATED_AT).toString())
		);
	}

	public void delete(UUID userId) {
		redisTemplate.delete(key(userId));
	}

	private String key(UUID userId) {
		return KEY_PREFIX + userId;
	}

	public record ProfitSummary(
			BigDecimal totalCurrentValue,
			BigDecimal totalProfitLoss,
			BigDecimal returnRate,
			LocalDateTime calculatedAt
	) {
	}
}
