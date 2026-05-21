package depth.finvibe.modules.study.application.port.out;

import depth.finvibe.modules.study.domain.AiStudyRecommend;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AiStudyRecommendRepository {
    boolean existsByUserIdAndLastModifiedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<AiStudyRecommend> findByUserId(Long userId);

    AiStudyRecommend save(AiStudyRecommend aiStudyRecommend);
}
