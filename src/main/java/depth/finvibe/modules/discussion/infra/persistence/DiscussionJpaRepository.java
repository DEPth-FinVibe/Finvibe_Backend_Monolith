package depth.finvibe.modules.discussion.infra.persistence;

import depth.finvibe.modules.discussion.domain.Discussion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscussionJpaRepository extends JpaRepository<Discussion, Long> {
    long countByNewsId(Long newsId);

    List<Discussion> findAllByNewsIdOrderByCreatedAtAsc(Long newsId);

    List<Discussion> findAllByOrderByCreatedAtDesc();

    List<Discussion> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT d.newsId as newsId, COUNT(d) as count FROM Discussion d WHERE d.newsId IN :newsIds GROUP BY d.newsId")
    List<NewsCountProjection> countByNewsIds(
            @org.springframework.data.repository.query.Param("newsIds") List<Long> newsIds);

    interface NewsCountProjection {
        Long getNewsId();

        Long getCount();
    }
}
