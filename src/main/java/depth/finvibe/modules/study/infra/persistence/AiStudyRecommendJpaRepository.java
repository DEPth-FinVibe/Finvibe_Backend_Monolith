package depth.finvibe.modules.study.infra.persistence;

import depth.finvibe.modules.study.domain.AiStudyRecommend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AiStudyRecommendJpaRepository extends JpaRepository<AiStudyRecommend, UUID> {
    boolean existsByUserIdAndLastModifiedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);
}
