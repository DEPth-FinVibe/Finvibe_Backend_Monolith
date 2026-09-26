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

    static final String INDEX_BUY_KEY_PREFIX = "market:reservation:buy:stock:";
    static final String INDEX_SELL_KEY_PREFIX = "market:reservation:sell:stock:";
    // 예약이 하나라도 있는 종목 집합. 전체 키를 훑는 KEYS 대신 이 집합으로 찾는다.
    static final String RESERVED_STOCKS_KEY = "market:reservation:stocks";
    // 예약이 바뀐 종목을 모든 노드에 알린다. 메시지는 종목 ID다.
    static final String CHANGED_CHANNEL = "market:reservation:changed";

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
        redisTemplate.opsForSet().add(RESERVED_STOCKS_KEY, String.valueOf(reservation.getStockId()));
        notifyChanged(reservation.getStockId());
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
        Long stockId = reservation.getStockId();
        if (isEmpty(keyForBuyIndex(stockId)) && isEmpty(keyForSellIndex(stockId))) {
            redisTemplate.opsForSet().remove(RESERVED_STOCKS_KEY, String.valueOf(stockId));
        }
        notifyChanged(stockId);
    }

    private boolean isEmpty(String indexKey) {
        Long size = redisTemplate.opsForZSet().zCard(indexKey);
        return size == null || size == 0;
    }

    private void notifyChanged(Long stockId) {
        redisTemplate.convertAndSend(CHANGED_CHANNEL, String.valueOf(stockId));
    }

    @Override
    public void clear() {
        redisTemplate.delete(redisTemplate.keys("market:reservation:*"));
    }

    @Override
    public List<Long> findReservedStockIds() {
        Set<String> members = redisTemplate.opsForSet().members(RESERVED_STOCKS_KEY);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .map(this::parseLongOrNull)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    /**
     * 예약 종목 집합을 도입하기 전의 데이터를 옮기기 위해 한 번만 쓴다. 전체 키를 훑으므로 평소에는 쓰지 않는다.
     */
    List<Long> findReservedStockIdsByKeyScan() {
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

    private Long parseLongOrNull(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    static String keyForBuyIndex(Long stockId) {
        return INDEX_BUY_KEY_PREFIX + stockId;
    }

    static String keyForSellIndex(Long stockId) {
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
