package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.idclass.UserMetricId;

public interface MetricJpaRepository extends JpaRepository<UserMetric, UserMetricId> {
    List<UserMetric> findByTypeAndCollectPeriodAndValueGreaterThanEqual(
            UserMetricType type,
            CollectPeriod collectPeriod,
            Double value);

    List<UserMetric> findByTypeAndCollectPeriodOrderByValueDesc(
            UserMetricType type,
            CollectPeriod collectPeriod,
            Pageable pageable);

    Optional<UserMetric> findByUserIdAndTypeAndCollectPeriod(UUID userId, UserMetricType type, CollectPeriod collectPeriod);

    List<UserMetric> findByUserId(UUID userId);

    List<UserMetric> findByUserIdAndTypeInAndCollectPeriod(UUID userId, List<UserMetricType> types, CollectPeriod collectPeriod);

    void deleteByCollectPeriod(CollectPeriod collectPeriod);
}
