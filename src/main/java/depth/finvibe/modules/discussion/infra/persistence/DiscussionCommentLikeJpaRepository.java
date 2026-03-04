package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.domain.DiscussionCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionCommentLikeJpaRepository extends JpaRepository<DiscussionCommentLike, Long> {
    long countByCommentId(Long commentId);

    void deleteByCommentId(Long commentId);

    void deleteByCommentDiscussionId(Long discussionId);

    Optional<DiscussionCommentLike> findByCommentIdAndUserId(Long commentId, UUID userId);
}
