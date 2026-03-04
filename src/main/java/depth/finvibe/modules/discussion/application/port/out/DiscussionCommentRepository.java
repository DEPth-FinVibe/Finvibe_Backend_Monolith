package depth.finvibe.modules.discussion.application.port.out;

import depth.finvibe.modules.discussion.domain.DiscussionComment;

import java.util.List;
import java.util.Optional;

public interface DiscussionCommentRepository {
    List<DiscussionComment> findAllByDiscussionIdOrderByCreatedAtAsc(Long discussionId);

    void deleteByDiscussionId(Long discussionId);

    DiscussionComment save(DiscussionComment comment);

    Optional<DiscussionComment> findById(Long id);

    void delete(DiscussionComment comment);
}
