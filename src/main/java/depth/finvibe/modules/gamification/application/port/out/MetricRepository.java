package depth.finvibe.modules.gamification.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.vo.Period;

public interface MetricRepository {
    List<UUID> findUsersAchieved(UserMetricType metricType, CollectPeriod collectPeriod, Double targetValue);

    List<UUID> findTopUsersByMetric(UserMetricType metricType, CollectPeriod collectPeriod, int limit);

    List<UUID> findUsersAchievedInPeriod(UserMetricType metricType, CollectPeriod collectPeriod, Double targetValue, Period period);

    Optional<UserMetric> findByUserIdAndType(UUID userId, UserMetricType type, CollectPeriod collectPeriod);

    List<UserMetric> findAllByUserId(UUID userId);

    List<UserMetric> findAllByUserIdAndTypes(UUID userId, List<UserMetricType> types, CollectPeriod collectPeriod);

    UserMetric save(UserMetric userMetric);

    void deleteAllByCollectPeriod(CollectPeriod collectPeriod);
}
