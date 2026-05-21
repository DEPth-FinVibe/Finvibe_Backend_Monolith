package depth.finvibe.modules.discussion.application.port.in;

import depth.finvibe.modules.discussion.dto.DiscussionDto;

import java.util.UUID;

public interface DiscussionCommandUseCase {
    DiscussionDto.Response addDiscussion(Long newsId, Long userId, String content);

    DiscussionDto.Response updateDiscussion(Long discussionId, Long userId, String content);

    void deleteDiscussion(Long discussionId, Long userId);

    void toggleDiscussionLike(Long discussionId, Long userId);
}
