package depth.finvibe.modules.asset.infra.redis;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포트폴리오별 보유 자산 스냅샷.
 * 수익률 계산 시 DB 조회 없이 Redis에서 보유 수량/매입가를 읽기 위해 사용한다.
 *
 * Key: pfh:{portfolioId}
 * Type: HASH
 * Field: a:{stockId} (Long → String)
 * Value: "{amount}|{purchasePriceAmount}|{currency}" (listpack 최적화: 64 bytes 이하)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PortfolioAssetSnapshotRedisRepository {

	private static final String KEY_PREFIX = "pfh:";
	private static final String ASSET_FIELD_PREFIX = "a:";
	private static final String SEPARATOR = "|";

	private final StringRedisTemplate redisTemplate;

	public void putAsset(Long portfolioId, Long stockId, BigDecimal amount, BigDecimal purchasePriceAmount, String currency) {
		String value = encode(amount, purchasePriceAmount, currency);
		redisTemplate.opsForHash().put(key(portfolioId), assetField(stockId), value);
	}

	public void removeAsset(Long portfolioId, Long stockId) {
		redisTemplate.opsForHash().delete(key(portfolioId), assetField(stockId));
	}

	public Map<Long, AssetSnapshot> getAssets(Long portfolioId) {
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(portfolioId));
		if (entries.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<Long, AssetSnapshot> result = new HashMap<>(entries.size());
		for (Map.Entry<Object, Object> entry : entries.entrySet()) {
			String field = entry.getKey().toString();
			if (!field.startsWith(ASSET_FIELD_PREFIX)) {
				continue;
			}
			Long stockId = Long.valueOf(field.substring(ASSET_FIELD_PREFIX.length()));
			AssetSnapshot snapshot = decode(entry.getValue().toString());
			if (snapshot != null) {
				result.put(stockId, snapshot);
			}
		}
		return result;
	}

	public void replaceAll(Long portfolioId, Map<Long, AssetSnapshot> assets) {
		String k = key(portfolioId);
		deleteAssetFields(k);
		if (assets != null && !assets.isEmpty()) {
			Map<String, String> hash = new HashMap<>(assets.size());
			for (Map.Entry<Long, AssetSnapshot> entry : assets.entrySet()) {
				AssetSnapshot s = entry.getValue();
				hash.put(assetField(entry.getKey()), encode(s.amount(), s.purchasePriceAmount(), s.currency()));
			}
			redisTemplate.opsForHash().putAll(k, hash);
		}
	}

	public void deleteAll(Long portfolioId) {
		deleteAssetFields(key(portfolioId));
	}

	private void deleteAssetFields(String key) {
		Set<Object> fields = redisTemplate.opsForHash().keys(key);
		if (fields == null || fields.isEmpty()) {
			return;
		}
		Object[] assetFields = fields.stream()
				.filter(field -> field != null && field.toString().startsWith(ASSET_FIELD_PREFIX))
				.toArray();
		if (assetFields.length > 0) {
			redisTemplate.opsForHash().delete(key, assetFields);
		}
	}

	private String key(Long portfolioId) {
		return KEY_PREFIX + portfolioId;
	}

	private String assetField(Long stockId) {
		return ASSET_FIELD_PREFIX + stockId;
	}

	private String encode(BigDecimal amount, BigDecimal purchasePriceAmount, String currency) {
		return amount.toPlainString() + SEPARATOR + purchasePriceAmount.toPlainString() + SEPARATOR + currency;
	}

	private AssetSnapshot decode(String value) {
		String[] parts = value.split("\\|", 3);
		if (parts.length != 3) {
			log.warn("Invalid asset snapshot format: {}", value);
			return null;
		}
		return new AssetSnapshot(
				new BigDecimal(parts[0]),
				new BigDecimal(parts[1]),
				parts[2]
		);
	}

	public record AssetSnapshot(BigDecimal amount, BigDecimal purchasePriceAmount, String currency) {
	}
}
