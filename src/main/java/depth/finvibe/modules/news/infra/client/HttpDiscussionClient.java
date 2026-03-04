package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;
import java.util.Map;

/**
 * Discussion 모듈의 HTTP API를 호출하기 위한 클라이언트
 * 실제 분산 환경에서는 다른 노드의 Discussion 서비스 엔드포인트를 호출합니다.
 */
@HttpExchange("/api/internal/discussions")
public interface HttpDiscussionClient {

    /**
     * 여러 뉴스의 토론 수를 벌크로 조회합니다.
     * 
     * @param newsIds 뉴스 ID 목록
     * @return Map<newsId, count>
     */
    @GetExchange("/counts")
    Map<Long, Long> getDiscussionCounts(@RequestParam("newsIds") List<Long> newsIds);

    /**
     * 특정 뉴스의 토론 목록을 조회합니다.
     */
    @GetExchange
    List<DiscussionDto.Response> getDiscussions(
            @RequestParam("newsId") Long newsId,
            @RequestParam(value = "sort", defaultValue = "LATEST") DiscussionSortType sortType);
}
