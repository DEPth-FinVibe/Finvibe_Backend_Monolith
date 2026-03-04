package depth.finvibe.modules.gamification.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.gamification.application.port.out.MetricRepository;
import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.vo.Period;

@Repository
@RequiredArgsConstructor
public class MetricRepositoryImpl implements MetricRepository {
    private final MetricJpaRepository metricJpaRepository;

    @Override
    public List<UUID> findUsersAchieved(UserMetricType metricType, CollectPeriod collectPeriod, Double targetValue) {
        return metricJpaRepository.findByTypeAndCollectPeriodAndValueGreaterThanEqual(metricType, collectPeriod, targetValue).stream()
                .map(UserMetric::getUserId)
                .toList();
    }

    @Override
    public List<UUID> findTopUsersByMetric(UserMetricType metricType, CollectPeriod collectPeriod, int limit) {
        return metricJpaRepository.findByTypeAndCollectPeriodOrderByValueDesc(
                        metricType,
                        collectPeriod,
                        PageRequest.of(0, limit))
                .stream()
                .map(UserMetric::getUserId)
                .toList();
    }

    @Override
    public List<UUID> findUsersAchievedInPeriod(UserMetricType metricType, CollectPeriod collectPeriod, Double targetValue, Period period) {
        return findUsersAchieved(metricType, collectPeriod, targetValue);
    }

    @Override
    public Optional<UserMetric> findByUserIdAndType(UUID userId, UserMetricType type, CollectPeriod collectPeriod) {
        return metricJpaRepository.findByUserIdAndTypeAndCollectPeriod(userId, type, collectPeriod);
    }

    @Override
    public List<UserMetric> findAllByUserId(UUID userId) {
        return metricJpaRepository.findByUserId(userId);
    }

    @Override
    public List<UserMetric> findAllByUserIdAndTypes(UUID userId, List<UserMetricType> types, CollectPeriod collectPeriod) {
        return metricJpaRepository.findByUserIdAndTypeInAndCollectPeriod(userId, types, collectPeriod);
    }

    @Override
    public UserMetric save(UserMetric userMetric) {
        return metricJpaRepository.save(userMetric);
    }

    @Override
    public void deleteAllByCollectPeriod(CollectPeriod collectPeriod) {
        metricJpaRepository.deleteByCollectPeriod(collectPeriod);
    }
}
