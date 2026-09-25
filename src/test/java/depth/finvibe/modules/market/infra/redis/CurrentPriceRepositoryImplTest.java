package depth.finvibe.modules.market.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;

import depth.finvibe.modules.market.domain.CurrentPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CurrentPriceRepositoryImplTest {

    private static final Long STOCK_ID = 4971L;
    private static final LocalDateTime TICK_AT = LocalDateTime.of(2026, 9, 25, 10, 0, 1);

    @Mock
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private CurrentPriceRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CurrentPriceRepositoryImpl(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("세 키를 같은 slot으로, 체결시각을 KST epoch 초로 넘기고 부여된 버전을 돌려준다")
    void saveIfNewer_assignedVersion_returnsVersion() {
        // given
        long version = 1_790_298_001_000_002L;
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(String.valueOf(version));

        // when
        OptionalLong result = repository.saveIfNewer(currentPrice());

        // then
        assertThat(result).hasValue(version);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(
                "market:current-price:{stock:4971}",
                "market:current-price-updated-at:{stock:4971}",
                "market:current-price-version:{stock:4971}"
        )), args.capture());
        Object[] captured = args.getValue();
        assertThat((String) captured[0]).endsWith("}").doesNotContain("priceVersion");
        assertThat(captured[1]).isEqualTo(String.valueOf(TICK_AT.atZone(ZoneId.of("Asia/Seoul")).toEpochSecond()));
        assertThat(captured[3]).isEqualTo("300000");
        assertThat(captured[4]).isEqualTo("604800000");
    }

    @Test
    @DisplayName("스크립트가 빈 값을 돌려주면 오래된 틱으로 보고 빈 결과를 돌려준다")
    void saveIfNewer_staleTick_returnsEmpty() {
        // given
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn("");

        // when
        OptionalLong result = repository.saveIfNewer(currentPrice());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("스크립트가 붙인 priceVersion이 있는 저장 JSON을 읽을 수 있다")
    void deserialize_storedJsonWithVersion() {
        // given
        String serialized = objectMapper.writeValueAsString(currentPrice());
        String stored = serialized.substring(0, serialized.length() - 1) + ",\"priceVersion\":1790298001000002}";

        // when
        CurrentPrice restored = objectMapper.readValue(stored, CurrentPrice.class);

        // then
        assertThat(restored.getPriceVersion()).isEqualTo(1_790_298_001_000_002L);
        assertThat(restored.getClose()).isEqualByComparingTo("71200");
    }

    private CurrentPrice currentPrice() {
        BigDecimal price = new BigDecimal("71200");
        return new CurrentPrice(STOCK_ID, TICK_AT, price, price, price, price, price,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null);
    }
}
