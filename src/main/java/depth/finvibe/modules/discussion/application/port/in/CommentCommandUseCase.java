package depth.finvibe.modules.discussion.application.port.in;

import depth.finvibe.modules.discussion.dto.DiscussionDto;

import java.util.UUID;

public interface CommentCommandUseCase {
    DiscussionDto.CommentResponse addComment(Long discussionId, Long userId, String content);

    DiscussionDto.CommentResponse updateComment(Long commentId, Long userId, String content);

    void deleteComment(Long commentId, Long userId);

    void toggleCommentLike(Long commentId, Long userId);
}
