package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.application.port.out.DiscussionLikeRepository;
import depth.finvibe.modules.discussion.domain.DiscussionLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DiscussionLikeRepositoryImpl implements DiscussionLikeRepository {

    private final DiscussionLikeJpaRepository discussionLikeJpaRepository;

    @Override
    public long countByDiscussionId(Long discussionId) {
        return discussionLikeJpaRepository.countByDiscussionId(discussionId);
    }

    @Override
    public void deleteByDiscussionId(Long discussionId) {
        discussionLikeJpaRepository.deleteByDiscussionId(discussionId);
    }

    @Override
    public DiscussionLike save(DiscussionLike like) {
        return discussionLikeJpaRepository.save(like);
    }

    @Override
    public void delete(DiscussionLike like) {
        discussionLikeJpaRepository.delete(like);
    }

    @Override
    public Optional<DiscussionLike> findByDiscussionIdAndUserId(Long discussionId, UUID userId) {
        return discussionLikeJpaRepository.findByDiscussionIdAndUserId(discussionId, userId);
    }
}
