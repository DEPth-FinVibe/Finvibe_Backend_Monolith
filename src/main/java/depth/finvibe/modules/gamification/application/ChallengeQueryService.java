package depth.finvibe.modules.gamification.application;

import depth.finvibe.modules.gamification.application.port.in.ChallengeQueryUseCase;
import depth.finvibe.modules.gamification.application.port.out.MetricRepository;
import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRepository;
import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRewardRepository;
import depth.finvibe.modules.gamification.domain.PersonalChallenge;
import depth.finvibe.modules.gamification.domain.PersonalChallengeReward;
import depth.finvibe.modules.gamification.domain.UserMetric;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.vo.Period;
import depth.finvibe.modules.gamification.dto.ChallengeDto;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeQueryService implements ChallengeQueryUseCase {

    private final PersonalChallengeRepository personalChallengeRepository;
    private final PersonalChallengeRewardRepository personalChallengeRewardRepository;
    private final MetricRepository metricRepository;

    @Override
    public List<ChallengeDto.ChallengeResponse> getPersonalChallenges(UUID userId) {
        Period currentPeriod = Period.ofWeek(LocalDate.now());
        List<PersonalChallenge> challenges = personalChallengeRepository.findAllByPeriod(currentPeriod);

        Map<UserMetricType, Double> userMetrics = getRequiredMetrics(userId, challenges);

        return challenges.stream()
                .map(challenge -> {
                    Double currentValue = userMetrics.getOrDefault(challenge.getCondition().getMetricType(), 0.0);
                    return ChallengeDto.ChallengeResponse.from(challenge, currentValue);
                })
                .toList();
    }

    private @NonNull Map<UserMetricType, Double> getRequiredMetrics(UUID userId, List<PersonalChallenge> challenges) {
        // 챌린지에서 사용하는 메트릭 타입들만 추출
        List<UserMetricType> requiredTypes = challenges.stream()
                .map(challenge -> challenge.getCondition().getMetricType())
                .distinct()
                .toList();

        List<UserMetricType> weeklyTypes = requiredTypes.stream()
                .filter(this::isWeeklyMetric)
                .toList();
        List<UserMetricType> nonWeeklyTypes = requiredTypes.stream()
                .filter(type -> !isWeeklyMetric(type))
                .toList();

        Map<UserMetricType, Double> metrics = metricRepository.findAllByUserIdAndTypes(
                        userId,
                        nonWeeklyTypes,
                        CollectPeriod.ALLTIME)
                .stream()
                .collect(Collectors.toMap(
                        UserMetric::getType,
                        UserMetric::getValue
                ));

        Map<UserMetricType, Double> weeklyMetrics = metricRepository.findAllByUserIdAndTypes(
                        userId,
                        weeklyTypes,
                        CollectPeriod.WEEKLY)
                .stream()
                .collect(Collectors.toMap(
                        UserMetric::getType,
                        UserMetric::getValue
                ));

        metrics.putAll(weeklyMetrics);
        return metrics;
    }

    private boolean isWeeklyMetric(UserMetricType metricType) {
        return metricType != null && metricType.isWeeklyCollect();
    }

    @Override
    public List<ChallengeDto.ChallengeHistoryResponse> getCompletedChallenges(UUID userId, int year, int month) {
        Period period = Period.ofMonth(year, month);
        List<PersonalChallengeReward> rewards = personalChallengeRewardRepository.findAllByUserIdAndPeriod(userId, period);

        if (rewards.isEmpty()) {
            return List.of();
        }

        List<Long> challengeIds = rewards.stream()
                .map(PersonalChallengeReward::getChallengeId)
                .toList();

        Map<Long, PersonalChallenge> challengeMap = personalChallengeRepository.findAllByIds(challengeIds).stream()
                .collect(Collectors.toMap(PersonalChallenge::getId, challenge -> challenge));

        return rewards.stream()
                .map(reward -> {
                    PersonalChallenge challenge = challengeMap.get(reward.getChallengeId());
                    if (challenge == null) {
                        return null;
                    }
                    LocalDate completedAt = reward.getCreatedAt() != null
                            ? reward.getCreatedAt().toLocalDate()
                            : challenge.getPeriod().getEndDate();
                    return ChallengeDto.ChallengeHistoryResponse.from(challenge, completedAt);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
