package depth.finvibe.modules.market.infra.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import depth.finvibe.modules.market.domain.enums.ReservationType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.market.application.port.out.ReservationRepository;
import depth.finvibe.modules.market.domain.Reservation;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private static final String INDEX_BUY_KEY_PREFIX = "market:reservation:buy:stock:";
    private static final String INDEX_SELL_KEY_PREFIX = "market:reservation:sell:stock:";

    private static final String INFO_KEY_PREFIX = "market:reservation:trade:";

    private static final Duration CURRENT_RESERVATION_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(Reservation reservation) {
        String indexKey;

        if(reservation.getType().equals(ReservationType.SELL)) {
            indexKey = keyForSellIndex(reservation.getStockId());
        } else {
            indexKey = keyForBuyIndex(reservation.getStockId());
        }

        String infoKey = keyForInfo(reservation.getTradeId());

        redisTemplate.opsForZSet().add(indexKey, String.valueOf(reservation.getTradeId()), reservation.getTargetPrice());

        String json = objectMapper.writeValueAsString(reservation);
        redisTemplate.opsForValue().set(infoKey, json, CURRENT_RESERVATION_TTL);
    }

    @Override
    public void deleteByTradeId(Long tradeId) {
        String infoKey = keyForInfo(tradeId);
        String reservationJson = redisTemplate.opsForValue().get(infoKey);

        if (reservationJson == null) {
            return;
        }

        Reservation reservation = objectMapper.readValue(
                reservationJson,
                Reservation.class
        );

        String indexKey;
        if(reservation.getType().equals(ReservationType.SELL)) {
            indexKey = keyForSellIndex(reservation.getStockId());
        } else {
            indexKey = keyForBuyIndex(reservation.getStockId());
        }

        redisTemplate.delete(infoKey);
        redisTemplate.opsForZSet().remove(indexKey, String.valueOf(tradeId));
    }

    @Override
    public void clear() {
        redisTemplate.delete(redisTemplate.keys("market:reservation:*"));
    }

    @Override
    public List<Long> findReservedStockIds() {
        Set<String> buyKeys = redisTemplate.keys(INDEX_BUY_KEY_PREFIX + "*");
        Set<String> sellKeys = redisTemplate.keys(INDEX_SELL_KEY_PREFIX + "*");

        if ((buyKeys == null || buyKeys.isEmpty()) && (sellKeys == null || sellKeys.isEmpty())) {
            return List.of();
        }

        return Stream.concat(
                        buyKeys == null ? Stream.empty() : buyKeys.stream(),
                        sellKeys == null ? Stream.empty() : sellKeys.stream()
                )
                .map(this::extractStockId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<Reservation> findBuyConditionMet(Long stockId, Long price) {
        //현재가(price)보다 더 높은 가격에 예약된 매수 주문들을 모두 조회
        String indexKey = keyForBuyIndex(stockId);

        Set<String> tradeIds = redisTemplate.opsForZSet()
                .rangeByScore(indexKey, price, Double.POSITIVE_INFINITY);

        return getDeserializedReservations(tradeIds);
    }

    @NonNull
    private List<Reservation> getDeserializedReservations(Set<String> tradeIds) {
        if (tradeIds == null || tradeIds.isEmpty()) {
            return List.of();
        }

        List<String> keys = tradeIds.stream()
                .map(tradeId -> keyForInfo(Long.valueOf(tradeId)))
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .flatMap(this::deserializeSafely)
                .toList();
    }

    @Override
    public List<Reservation> findSellConditionMet(Long stockId, Long price) {
        //현재가(price)보다 더 낮은 가격에 예약된 매도 주문들을 모두 조회
        String indexKey = keyForSellIndex(stockId);

        Set<String> tradeIds = redisTemplate.opsForZSet()
                .rangeByScore(indexKey, Double.NEGATIVE_INFINITY, price);
        return getDeserializedReservations(tradeIds);
    }

    private String keyForBuyIndex(Long stockId) {
        return INDEX_BUY_KEY_PREFIX + stockId;
    }

    private String keyForSellIndex(Long stockId) {
        return INDEX_SELL_KEY_PREFIX + stockId;
    }

    private String keyForInfo(Long tradeId) {
        return INFO_KEY_PREFIX + tradeId;
    }

    private Stream<Reservation> deserializeSafely(String value) {
        try {
            return Stream.of(objectMapper.readValue(value, Reservation.class));
        } catch (JacksonIOException ex) {
            throw new IllegalStateException("Failed to deserialize reservation", ex);
        }
    }

    private Long extractStockId(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith(INDEX_BUY_KEY_PREFIX)) {
            return parseStockId(key, INDEX_BUY_KEY_PREFIX);
        }
        if (key.startsWith(INDEX_SELL_KEY_PREFIX)) {
            return parseStockId(key, INDEX_SELL_KEY_PREFIX);
        }
        return null;
    }

    private Long parseStockId(String key, String prefix) {
        String raw = key.substring(prefix.length());
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
