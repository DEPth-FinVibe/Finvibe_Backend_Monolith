package depth.finvibe.modules.news.infra.adapter;

import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;
import depth.finvibe.modules.news.application.port.out.NewsDiscussionPort;
import depth.finvibe.modules.news.infra.client.HttpDiscussionClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class NewsDiscussionHttpClientAdapter implements NewsDiscussionPort {

    private final HttpDiscussionClient httpDiscussionClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker discussionCountBreaker() {
        return circuitBreakerRegistry.circuitBreaker("discussionServiceCount");
    }

    private CircuitBreaker discussionCountsBreaker() {
        return circuitBreakerRegistry.circuitBreaker("discussionServiceCounts");
    }

    private CircuitBreaker discussionsBreaker() {
        return circuitBreakerRegistry.circuitBreaker("discussionServiceList");
    }

    private <T> T run(CircuitBreaker breaker, Supplier<T> supplier, Function<Throwable, T> fallback) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(breaker, supplier);
        try {
            return decorated.get();
        } catch (Exception ex) {
            return fallback.apply(ex);
        }
    }

    @Override
    public long getDiscussionCount(Long newsId) {
        return run(
                discussionCountBreaker(),
                () -> {
                    Map<Long, Long> counts = httpDiscussionClient.getDiscussionCounts(List.of(newsId));
                    return counts.getOrDefault(newsId, 0L);
                },
                ex -> 0L
        );
    }

    /**
     * HTTP를 통해 외부 Discussion 서비스의 벌크 카운트 API를 호출합니다.
     * 분산 환경에서는 다른 노드의 엔드포인트를 호출하게 됩니다.
     */
    @Override
    public Map<Long, Long> getDiscussionCounts(List<Long> newsIds) {
        return run(
                discussionCountsBreaker(),
                () -> httpDiscussionClient.getDiscussionCounts(newsIds),
                ex -> Map.of()
        );
    }

    @Override
    public List<DiscussionDto.Response> getDiscussions(Long newsId, DiscussionSortType sortType) {
        return run(
                discussionsBreaker(),
                () -> httpDiscussionClient.getDiscussions(newsId, sortType),
                ex -> List.of()
        );
    }
}
