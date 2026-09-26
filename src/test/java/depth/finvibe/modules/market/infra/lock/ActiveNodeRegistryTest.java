package depth.finvibe.modules.market.infra.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiveNodeRegistryTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RScoredSortedSet<Object> nodes;
    @Mock
    private RBucket<Object> bucket;
    @Mock
    private RKeys keys;

    private ActiveNodeRegistry registry;

    @BeforeEach
    void setUp() {
        when(redissonClient.getScoredSortedSet(ActiveNodeRegistry.NODE_SET_KEY)).thenReturn(nodes);
        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.getKeys()).thenReturn(keys);
        registry = new ActiveNodeRegistry(redissonClient);
        registry.initialize();
    }

    @Test
    @DisplayName("heartbeat는 활성 노드 목록에 노드 ID와 현재 시각을 기록하고, 이전 버전 호환 키도 남긴다")
    void recordHeartbeat_addsToNodeSet() {
        verify(nodes).add(anyDouble(), eq(registry.getNodeId()));
        verify(bucket).set(anyLong(), eq(15L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("활성 노드 수는 만료된 노드를 지운 뒤 목록 크기로 세고, 전체 키를 스캔하지 않는다")
    void getActiveNodeCount_usesNodeSetWithoutScan() {
        when(nodes.size()).thenReturn(2);

        int count = registry.getActiveNodeCount();

        assertThat(count).isEqualTo(2);
        verify(nodes).removeRangeByScore(eq(0.0), eq(true), anyDouble(), eq(false));
        verify(keys, never()).getKeysByPattern(anyString());
    }

    @Test
    @DisplayName("활성 노드가 0개로 조회되면 1을 돌려준다")
    void getActiveNodeCount_emptyReturnsOne() {
        when(nodes.size()).thenReturn(0);

        assertThat(registry.getActiveNodeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("종료하면 활성 노드 목록에서 자신을 지운다")
    void shutdown_removesFromNodeSet() {
        registry.shutdown();

        verify(nodes).remove(registry.getNodeId());
        verify(bucket).delete();
    }
}
