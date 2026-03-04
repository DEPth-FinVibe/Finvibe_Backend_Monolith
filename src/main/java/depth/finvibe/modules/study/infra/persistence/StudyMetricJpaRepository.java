package depth.finvibe.modules.study.infra.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.study.domain.StudyMetric;

public interface StudyMetricJpaRepository extends JpaRepository<StudyMetric, UUID> {
}
