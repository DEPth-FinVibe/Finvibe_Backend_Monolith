package depth.finvibe.modules.discussion.application.port.out;

import depth.finvibe.modules.discussion.domain.DiscussionCommentLike;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionCommentLikeRepository {
    long countByCommentId(Long commentId);

    void deleteByCommentId(Long commentId);

    void deleteByDiscussionId(Long discussionId);

    DiscussionCommentLike save(DiscussionCommentLike like);

    void delete(DiscussionCommentLike like);

    Optional<DiscussionCommentLike> findByCommentIdAndUserId(Long commentId, UUID userId);
}
