package depth.finvibe.modules.gamification.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.vo.Period;

public interface MetricRepository {
    List<Long> findUsersAchieved(UserMetricType metricType, CollectPeriod collectPeriod, Double targetValue);

    List<Long> findTopUsersByMetric(UserMetricType metricType, CollectPeriod collectPeriod, int limit);

    List<Long> findUsersAchievedInPeriod(UserMetricType metricType, CollectPeriod collectPeriod, Double targetValue, Period period);

    Optional<UserMetric> findByUserIdAndType(Long userId, UserMetricType type, CollectPeriod collectPeriod);

    List<UserMetric> findAllByUserId(Long userId);

    List<UserMetric> findAllByUserIdAndTypes(Long userId, List<UserMetricType> types, CollectPeriod collectPeriod);

    UserMetric save(UserMetric userMetric);

    void deleteAllByCollectPeriod(CollectPeriod collectPeriod);
}
