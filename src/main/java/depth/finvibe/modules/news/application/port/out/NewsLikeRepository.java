package depth.finvibe.modules.news.application.port.out;

import depth.finvibe.modules.news.domain.NewsLike;

import java.util.Optional;
import java.util.UUID;

public interface NewsLikeRepository {
    long countByNewsId(Long newsId);

    NewsLike save(NewsLike like);

    void delete(NewsLike like);

    Optional<NewsLike> findByNewsIdAndUserId(Long newsId, UUID userId);
}
