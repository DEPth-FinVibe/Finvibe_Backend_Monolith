package depth.finvibe.modules.discussion.application.port.out;

import depth.finvibe.modules.discussion.domain.Discussion;

import java.util.List;
import java.util.Optional;

public interface DiscussionRepository {
    long countByNewsId(Long newsId);

    java.util.Map<Long, Long> countByNewsIds(java.util.List<Long> newsIds);

    List<Discussion> findAllByNewsIdOrderByCreatedAtAsc(Long newsId);

    List<Discussion> findAllOrderByCreatedAtDesc();

    List<Discussion> findAll();

    Discussion save(Discussion discussion);

    Optional<Discussion> findById(Long id);

    void delete(Discussion discussion);
}
