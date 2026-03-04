package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.application.port.out.DiscussionCommentRepository;
import depth.finvibe.modules.discussion.domain.DiscussionComment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DiscussionCommentRepositoryImpl implements DiscussionCommentRepository {

    private final DiscussionCommentJpaRepository jpaRepository;

    @Override
    public List<DiscussionComment> findAllByDiscussionIdOrderByCreatedAtAsc(Long discussionId) {
        return jpaRepository.findAllByDiscussionIdOrderByCreatedAtAsc(discussionId);
    }

    @Override
    public void deleteByDiscussionId(Long discussionId) {
        jpaRepository.deleteByDiscussionId(discussionId);
    }

    @Override
    public DiscussionComment save(DiscussionComment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public Optional<DiscussionComment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(DiscussionComment comment) {
        jpaRepository.delete(comment);
    }
}
