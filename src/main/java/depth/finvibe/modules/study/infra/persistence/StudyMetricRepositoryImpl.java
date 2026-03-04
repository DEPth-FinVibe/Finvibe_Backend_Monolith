package depth.finvibe.modules.study.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.study.application.port.out.StudyMetricRepository;
import depth.finvibe.modules.study.domain.StudyMetric;

@Repository
@RequiredArgsConstructor
public class StudyMetricRepositoryImpl implements StudyMetricRepository {
    private final StudyMetricJpaRepository studyMetricJpaRepository;

    @Override
    public Optional<StudyMetric> findByUserId(UUID userId) {
        return studyMetricJpaRepository.findById(userId);
    }

    @Override
    public StudyMetric save(StudyMetric studyMetric) {
        return studyMetricJpaRepository.save(studyMetric);
    }
}
