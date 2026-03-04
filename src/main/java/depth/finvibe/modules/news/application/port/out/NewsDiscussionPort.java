package depth.finvibe.modules.news.application.port.out;

import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;

import java.util.List;
import java.util.Map;

public interface NewsDiscussionPort {
    /**
     * 특정 뉴스의 현재 토론 수를 조회합니다.
     */
    long getDiscussionCount(Long newsId);

    /**
     * 여러 뉴스의 토론 수를 한 번에 조회합니다. (분산 환경 최적화)
     */
    Map<Long, Long> getDiscussionCounts(List<Long> newsIds);

    /**
     * 특정 뉴스의 토론 목록을 조회합니다.
     */
    List<DiscussionDto.Response> getDiscussions(Long newsId, DiscussionSortType sortType);
}
