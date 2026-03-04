package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentLikeRepository;
import depth.finvibe.modules.discussion.domain.DiscussionCommentLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DiscussionCommentLikeRepositoryImpl implements DiscussionCommentLikeRepository {

    private final DiscussionCommentLikeJpaRepository discussionCommentLikeJpaRepository;

    @Override
    public long countByCommentId(Long commentId) {
        return discussionCommentLikeJpaRepository.countByCommentId(commentId);
    }

    @Override
    public void deleteByCommentId(Long commentId) {
        discussionCommentLikeJpaRepository.deleteByCommentId(commentId);
    }

    @Override
    public void deleteByDiscussionId(Long discussionId) {
        discussionCommentLikeJpaRepository.deleteByCommentDiscussionId(discussionId);
    }

    @Override
    public DiscussionCommentLike save(DiscussionCommentLike like) {
        return discussionCommentLikeJpaRepository.save(like);
    }

    @Override
    public void delete(DiscussionCommentLike like) {
        discussionCommentLikeJpaRepository.delete(like);
    }

    @Override
    public Optional<DiscussionCommentLike> findByCommentIdAndUserId(Long commentId, UUID userId) {
        return discussionCommentLikeJpaRepository.findByCommentIdAndUserId(commentId, userId);
    }
}
