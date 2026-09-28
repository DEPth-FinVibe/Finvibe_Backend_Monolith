package depth.finvibe.modules.market.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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

    @Mock
    private MarketRedisPipeline marketRedisPipeline;

    @Mock
    private RedisAdvancedClusterAsyncCommands<String, String> clusterCommands;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private CurrentPriceRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CurrentPriceRepositoryImpl(redisTemplate, objectMapper, marketRedisPipeline, meterRegistry);
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

    @Test
    @DisplayName("묶음 저장은 틱마다 EVALSHA를 파이프라인으로 보내고 입력 순서대로 버전을 돌려준다")
    void saveAllIfNewer_pipeline_returnsVersionsInOrder() throws Exception {
        // given
        givenPipeline();
        RedisFuture<String> first = completed("1790298001000000");
        RedisFuture<String> stale = completed("");
        when(clusterCommands.<String>evalsha(anyString(), eq(ScriptOutputType.VALUE), any(String[].class), any(String[].class)))
                .thenReturn(first, stale);

        // when
        List<OptionalLong> result = repository.saveAllIfNewer(List.of(currentPrice(), currentPrice()));

        // then
        assertThat(result).containsExactly(OptionalLong.of(1_790_298_001_000_000L), OptionalLong.empty());
        ArgumentCaptor<String[]> keys = ArgumentCaptor.forClass(String[].class);
        verify(clusterCommands, times(2)).evalsha(anyString(), eq(ScriptOutputType.VALUE), keys.capture(), any(String[].class));
        assertThat(keys.getValue()).containsExactly(
                "market:current-price:{stock:4971}",
                "market:current-price-updated-at:{stock:4971}",
                "market:current-price-version:{stock:4971}");
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("노드에 스크립트가 없으면 본문(EVAL)으로 다시 보낸다")
    void saveAllIfNewer_noScript_retriesWithEval() throws Exception {
        // given
        givenPipeline();
        RedisFuture<String> missing = org.mockito.Mockito.mock(RedisFuture.class);
        when(missing.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new ExecutionException(new RedisNoScriptException("NOSCRIPT")));
        when(clusterCommands.<String>evalsha(anyString(), eq(ScriptOutputType.VALUE), any(String[].class), any(String[].class)))
                .thenReturn(missing);
        RedisFuture<String> retried = completed("1790298001000000");
        when(clusterCommands.<String>eval(anyString(), eq(ScriptOutputType.VALUE), any(String[].class), any(String[].class)))
                .thenReturn(retried);

        // when
        List<OptionalLong> result = repository.saveAllIfNewer(List.of(currentPrice()));

        // then
        assertThat(result).containsExactly(OptionalLong.of(1_790_298_001_000_000L));
    }

    @Test
    @DisplayName("파이프라인을 쓸 수 없으면(로컬 등) 한 건씩 저장한다")
    void saveAllIfNewer_pipelineUnavailable_fallsBackToSingle() {
        // given
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn("7");

        // when
        List<OptionalLong> result = repository.saveAllIfNewer(List.of(currentPrice(), currentPrice()));

        // then
        assertThat(result).containsExactly(OptionalLong.of(7L), OptionalLong.of(7L));
    }

    @Test
    @DisplayName("전환 중 실패한 틱은 종목마다 처음 실패한 틱부터 뒤의 틱까지 다시 보내고, 다른 종목은 다시 보내지 않는다")
    void saveAllIfNewer_failedTicks_retriesFromFirstFailurePerStock() throws Exception {
        // given: 종목 A의 첫 틱이 실패하고, A의 둘째 틱과 종목 B는 성공한다.
        givenPipeline();
        RedisFuture<String> failed = failed();
        RedisFuture<String> a2 = completed("11");
        RedisFuture<String> b1 = completed("21");
        RedisFuture<String> a1Retried = completed("12");
        RedisFuture<String> a2Retried = completed("13");
        when(clusterCommands.<String>evalsha(anyString(), eq(ScriptOutputType.VALUE), any(String[].class), any(String[].class)))
                .thenReturn(failed, a2, b1, a1Retried, a2Retried);
        List<CurrentPrice> ticks = List.of(currentPrice(1L), currentPrice(1L), currentPrice(2L));

        // when
        List<OptionalLong> result = repository.saveAllIfNewer(ticks);

        // then: 두 번째 시도는 A의 두 틱만 입력 순서대로 보낸다.
        assertThat(result).containsExactly(OptionalLong.of(12L), OptionalLong.of(13L), OptionalLong.of(21L));
        ArgumentCaptor<String[]> keys = ArgumentCaptor.forClass(String[].class);
        verify(clusterCommands, times(5)).evalsha(anyString(), eq(ScriptOutputType.VALUE), keys.capture(), any(String[].class));
        assertThat(keys.getAllValues().subList(3, 5)).extracting(k -> k[0])
                .containsExactly("market:current-price:{stock:1}", "market:current-price:{stock:1}");
        assertThat(meterRegistry.counter("market.current_price.save_retried").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("재시도 시간 안에 복구되지 않으면 예외를 던진다")
    void saveAllIfNewer_retryWindowExceeded_throws() throws Exception {
        // given
        givenPipeline();
        repository.setRetryWindow(java.time.Duration.ofMillis(250));
        RedisFuture<String> failed = failed();
        when(clusterCommands.<String>evalsha(anyString(), eq(ScriptOutputType.VALUE), any(String[].class), any(String[].class)))
                .thenReturn(failed);

        // when & then: 100ms 뒤 한 번 더 시도하고, 다음 200ms 대기는 한도를 넘어 멈춘다.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.saveAllIfNewer(List.of(currentPrice())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after retries");
        verify(clusterCommands, times(2)).evalsha(anyString(), eq(ScriptOutputType.VALUE), any(String[].class), any(String[].class));
    }

    private CurrentPrice currentPrice(Long stockId) {
        BigDecimal price = new BigDecimal("71200");
        return new CurrentPrice(stockId, TICK_AT, price, price, price, price, price,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null);
    }

    @SuppressWarnings("unchecked")
    private RedisFuture<String> failed() throws Exception {
        RedisFuture<String> future = org.mockito.Mockito.mock(RedisFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new ExecutionException(new io.lettuce.core.RedisConnectionException("Connection closed")));
        return future;
    }

    @SuppressWarnings("unchecked")
    private void givenPipeline() {
        when(marketRedisPipeline.isAvailable()).thenReturn(true);
        when(marketRedisPipeline.submit(any())).thenAnswer(invocation ->
                ((Function<RedisAdvancedClusterAsyncCommands<String, String>, List<RedisFuture<String>>>) invocation.getArgument(0))
                        .apply(clusterCommands));
    }

    @SuppressWarnings("unchecked")
    private RedisFuture<String> completed(String value) throws Exception {
        RedisFuture<String> future = org.mockito.Mockito.mock(RedisFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(value);
        return future;
    }
}
