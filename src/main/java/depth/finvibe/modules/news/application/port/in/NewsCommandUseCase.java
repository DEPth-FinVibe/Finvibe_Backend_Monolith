package depth.finvibe.modules.news.application.port.in;

import java.util.UUID;

public interface NewsCommandUseCase {
    void syncLatestNews();

    void syncAllDiscussionCounts();

    void toggleNewsLike(Long newsId, UUID userId);
}
