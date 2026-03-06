package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.discussion.application.port.in.DiscussionQueryUseCase;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;
import depth.finvibe.modules.news.application.port.out.NewsDiscussionPort;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NewsDiscussionClientImpl implements NewsDiscussionPort {

    private final DiscussionQueryUseCase discussionQueryUseCase;

    @Override
    public long getDiscussionCount(Long newsId) {
        return discussionQueryUseCase.countByNewsId(newsId);
    }

    @Override
    public Map<Long, Long> getDiscussionCounts(List<Long> newsIds) {
        return discussionQueryUseCase.countByNewsIds(newsIds);
    }

    @Override
    public List<DiscussionDto.Response> getDiscussions(Long newsId, DiscussionSortType sortType) {
        return discussionQueryUseCase.findAllByNewsId(newsId, sortType);
    }
}
