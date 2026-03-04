package depth.finvibe.modules.discussion.application.port.in;

import depth.finvibe.modules.discussion.dto.DiscussionDto;

import java.util.UUID;

public interface DiscussionCommandUseCase {
    DiscussionDto.Response addDiscussion(Long newsId, UUID userId, String content);

    DiscussionDto.Response updateDiscussion(Long discussionId, UUID userId, String content);

    void deleteDiscussion(Long discussionId, UUID userId);

    void toggleDiscussionLike(Long discussionId, UUID userId);
}
