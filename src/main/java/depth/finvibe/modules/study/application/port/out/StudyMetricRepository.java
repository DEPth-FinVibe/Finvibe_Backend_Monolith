package depth.finvibe.modules.study.application.port.out;

import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.study.domain.StudyMetric;

public interface StudyMetricRepository {
    Optional<StudyMetric> findByUserId(UUID userId);
    StudyMetric save(StudyMetric studyMetric);
}
