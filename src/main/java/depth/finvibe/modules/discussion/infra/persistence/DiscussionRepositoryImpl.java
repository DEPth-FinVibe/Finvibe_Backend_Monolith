package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.application.port.out.DiscussionRepository;
import depth.finvibe.modules.discussion.domain.Discussion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DiscussionRepositoryImpl implements DiscussionRepository {

    private final DiscussionJpaRepository discussionJpaRepository;

    @Override
    public long countByNewsId(Long newsId) {
        return discussionJpaRepository.countByNewsId(newsId);
    }

    @Override
    public java.util.Map<Long, Long> countByNewsIds(java.util.List<Long> newsIds) {
        return discussionJpaRepository.countByNewsIds(newsIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        DiscussionJpaRepository.NewsCountProjection::getNewsId,
                        DiscussionJpaRepository.NewsCountProjection::getCount));
    }

    @Override
    public List<Discussion> findAllByNewsIdOrderByCreatedAtAsc(Long newsId) {
        return discussionJpaRepository.findAllByNewsIdOrderByCreatedAtAsc(newsId);
    }

    @Override
    public List<Discussion> findAllOrderByCreatedAtDesc() {
        return discussionJpaRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Discussion> findAll() {
        return discussionJpaRepository.findAll();
    }

    @Override
    public Discussion save(Discussion discussion) {
        return discussionJpaRepository.save(discussion);
    }

    @Override
    public Optional<Discussion> findById(Long id) {
        return discussionJpaRepository.findById(id);
    }

    @Override
    public void delete(Discussion discussion) {
        discussionJpaRepository.delete(discussion);
    }
}
