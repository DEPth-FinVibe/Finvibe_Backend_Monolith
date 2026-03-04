package depth.finvibe.modules.news.infra.persistence;

import depth.finvibe.modules.news.domain.NewsLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NewsLikeJpaRepository extends JpaRepository<NewsLike, Long> {
    long countByNewsId(Long newsId);

    Optional<NewsLike> findByNewsIdAndUserId(Long newsId, UUID userId);
}
