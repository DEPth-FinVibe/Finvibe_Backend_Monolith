package depth.finvibe.modules.discussion.application.port.out;

import depth.finvibe.modules.discussion.domain.DiscussionLike;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionLikeRepository {
    long countByDiscussionId(Long discussionId);

    void deleteByDiscussionId(Long discussionId);

    DiscussionLike save(DiscussionLike like);

    void delete(DiscussionLike like);

    Optional<DiscussionLike> findByDiscussionIdAndUserId(Long discussionId, UUID userId);
}
