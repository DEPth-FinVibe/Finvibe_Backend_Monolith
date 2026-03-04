package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.domain.DiscussionComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscussionCommentJpaRepository extends JpaRepository<DiscussionComment, Long> {
    List<DiscussionComment> findAllByDiscussionIdOrderByCreatedAtAsc(Long discussionId);

    void deleteByDiscussionId(Long discussionId);
}
