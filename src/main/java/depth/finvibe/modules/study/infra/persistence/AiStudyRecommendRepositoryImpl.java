package depth.finvibe.modules.study.infra.persistence;

import depth.finvibe.modules.study.application.port.out.AiStudyRecommendRepository;
import depth.finvibe.modules.study.domain.AiStudyRecommend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AiStudyRecommendRepositoryImpl implements AiStudyRecommendRepository {
    private final AiStudyRecommendJpaRepository aiStudyRecommendJpaRepository;

    @Override
    public boolean existsByUserIdAndLastModifiedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end) {
        return aiStudyRecommendJpaRepository.existsByUserIdAndLastModifiedAtBetween(userId, start, end);
    }

    @Override
    public Optional<AiStudyRecommend> findByUserId(UUID userId) {
        return aiStudyRecommendJpaRepository.findById(userId);
    }

    @Override
    public AiStudyRecommend save(AiStudyRecommend aiStudyRecommend) {
        return aiStudyRecommendJpaRepository.save(aiStudyRecommend);
    }
}
