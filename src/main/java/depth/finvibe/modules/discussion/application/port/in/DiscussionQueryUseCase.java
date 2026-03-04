package depth.finvibe.modules.discussion.application.port.in;

import depth.finvibe.modules.discussion.dto.DiscussionCommentDto;
import depth.finvibe.modules.discussion.dto.DiscussionDto;
import depth.finvibe.modules.discussion.dto.DiscussionSortType;

import java.util.List;

public interface DiscussionQueryUseCase {
    long countByNewsId(Long newsId);

    java.util.Map<Long, Long> countByNewsIds(java.util.List<Long> newsIds);

    List<DiscussionDto.Response> findAllByNewsId(Long newsId, DiscussionSortType sortType);

    List<DiscussionDto.Response> findAll(DiscussionSortType sortType);

    List<DiscussionDto.CommentResponse> findCommentsByDiscussionId(Long discussionId);
}
