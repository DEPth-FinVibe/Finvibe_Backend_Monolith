package depth.finvibe.modules.gamification.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import depth.finvibe.modules.gamification.application.port.in.ChallengeCommandUseCase;
import depth.finvibe.modules.gamification.application.port.out.ChallengeGenerator;
import depth.finvibe.modules.gamification.application.port.out.MetricRepository;
import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRepository;
import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRewardRepository;
import depth.finvibe.common.gamification.messaging.UserMetricUpdatedEventPublisher;
import depth.finvibe.modules.gamification.application.port.out.XpRewardEventPublisher;
import depth.finvibe.modules.gamification.domain.PersonalChallenge;
import depth.finvibe.modules.gamification.domain.PersonalChallengeReward;
import depth.finvibe.modules.gamification.domain.enums.CollectPeriod;
import depth.finvibe.modules.gamification.domain.enums.MetricEventType;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.domain.enums.WeeklyEventType;
import depth.finvibe.modules.gamification.domain.vo.ChallengeCondition;
import depth.finvibe.modules.gamification.domain.vo.Period;
import depth.finvibe.modules.gamification.domain.vo.Reward;
import depth.finvibe.modules.gamification.dto.ChallengeDto;
import depth.finvibe.common.gamification.dto.UserMetricUpdatedEvent;
import depth.finvibe.common.gamification.dto.XpRewardEvent;

@Service
@RequiredArgsConstructor
public class ChallengeService implements ChallengeCommandUseCase {

    private final ChallengeGenerator challengeGenerator;
    private final PersonalChallengeRepository personalChallengeRepository;
    private final MetricRepository metricRepository;
    private final PersonalChallengeRewardRepository personalChallengeRewardRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final XpRewardEventPublisher xpRewardEventPublisher;
    private final UserMetricUpdatedEventPublisher userMetricUpdatedEventPublisher;

    @Override
    @Transactional
    public void generatePersonalChallenges() {
        List<ChallengeDto.ChallengeGenerationResponse> generated = challengeGenerator.generate();

        if(generated.size() != 3) {
            throw new IllegalStateException("Generated challenges size is not equal to 3");
        }

        List<PersonalChallenge> personalChallenges = generated.stream()
                .map(this::toPersonalChallenge)
                .toList();

        personalChallengeRepository.saveAll(personalChallenges);
    }

    private PersonalChallenge toPersonalChallenge(ChallengeDto.ChallengeGenerationResponse response) {
        return PersonalChallenge.of(
                response.getTitle(),
                response.getDescription(),
                ChallengeCondition.of(response.getMetricType(), response.getTargetValue()),
                Period.ofWeek(LocalDate.now()),
                Reward.of(response.getRewardXp(), null)
        );
    }

    @Override
    @Transactional
    public void rewardPersonalChallenges() {
        Period currentPeriod = Period.ofWeek(LocalDate.now());
        List<PersonalChallenge> personalChallenges = personalChallengeRepository.findAllByPeriod(currentPeriod);

        if (personalChallenges.isEmpty()) {
            return;
        }

        List<PersonalChallengeReward> rewards = new ArrayList<>();

        personalChallenges.forEach(this::rewardUsersByChallenge);

        personalChallengeRewardRepository.saveAll(rewards);
    }

    private void rewardUsersByChallenge(PersonalChallenge personalChallenge) {
        ChallengeCondition condition = personalChallenge.getCondition();
        List<UUID> achievedUserIds = getWeeklyAchievedUsers(condition.getMetricType(), condition.getTargetValue());

        List<PersonalChallengeReward> toSave = achievedUserIds.stream()
                .map(userId -> toPersonalChallengeReward(personalChallenge, userId))
                .toList();

        personalChallengeRewardRepository.saveAll(toSave);

        rewardXpToEachUsers(personalChallenge, achievedUserIds);
        publishChallengeCompletedEvents(achievedUserIds);
    }



    private void rewardXpToEachUsers(PersonalChallenge personalChallenge, List<UUID> achievedUserIds) {
        achievedUserIds.forEach(userId -> publishXpRewardEvent(
                userId,
                String.format("[%s] 챌린지 보상", personalChallenge.getTitle()),
                personalChallenge.getReward().getRewardXp()
        ));
    }

    private void publishXpRewardEvent(UUID userId, String reason, Long rewardXp) {
        // 어플리케이션 내부 이벤트 발행 (트랜잭션 내)
        applicationEventPublisher.publishEvent(
                XpRewardEvent.of(
                        userId.toString(),
                        reason,
                        rewardXp
                )
        );
    }

    /**
     * 트랜잭션 커밋 후 외부 Kafka 이벤트 발행
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleXpRewardEventForKafka(XpRewardEvent event) {
        xpRewardEventPublisher.publishXpRewardEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleUserMetricUpdatedEventForKafka(UserMetricUpdatedEvent event) {
        userMetricUpdatedEventPublisher.publishUserMetricUpdatedEvent(event);
    }

    private static PersonalChallengeReward toPersonalChallengeReward(PersonalChallenge personalChallenge, UUID userId) {
        return PersonalChallengeReward.of(
                personalChallenge.getId(),
                userId,
                personalChallenge.getPeriod(),
                personalChallenge.getReward()
        );
    }

    @Override
    @Transactional
    public void rewardWeeklyChallenges() {
        // 주말 거래 토너먼트: 현재 수익률 상위 10명
        List<UUID> weekendTournamentUsers = metricRepository.findTopUsersByMetric(
                UserMetricType.CURRENT_RETURN_RATE,
                CollectPeriod.ALLTIME,
                10
        );
        rewardWeeklyEventUsers(WeeklyEventType.WEEKEND_TRADING_TOURNAMENT, weekendTournamentUsers, 1000L);

        // 챌린지 이벤트: 지난 주 챌린지 3개 이상 달성한 유저
        List<UUID> challengeEventUsers = getWeeklyAchievedUsers(
                UserMetricType.CHALLENGE_COMPLETION_COUNT,
                3.0
        );
        rewardWeeklyEventUsers(WeeklyEventType.CHALLENGE_EVENT, challengeEventUsers, 50L);
    }

    private List<UUID> getWeeklyAchievedUsers(UserMetricType metricType, Double targetValue) {
        if (metricType == null) {
            return List.of();
        }

        if (isWeeklyMetric(metricType)) {
            return metricRepository.findUsersAchieved(metricType, CollectPeriod.WEEKLY, targetValue);
        }

        return metricRepository.findUsersAchieved(metricType, CollectPeriod.ALLTIME, targetValue);
    }

    private boolean isWeeklyMetric(UserMetricType metricType) {
        return metricType != null && metricType.isWeeklyCollect();
    }

    private void rewardWeeklyEventUsers(WeeklyEventType eventType, List<UUID> userIds, Long rewardXp) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        String reason = String.format("[%s] 주간 이벤트 보상", getWeeklyEventTitle(eventType));
        userIds.forEach(userId -> publishXpRewardEvent(userId, reason, rewardXp));
    }

    private void publishChallengeCompletedEvents(List<UUID> achievedUserIds) {
        if (achievedUserIds == null || achievedUserIds.isEmpty()) {
            return;
        }

        Instant occurredAt = Instant.now();
        achievedUserIds.forEach(userId -> applicationEventPublisher.publishEvent(
                UserMetricUpdatedEvent.builder()
                        .userId(userId.toString())
                        .eventType(MetricEventType.CHALLENGE_COMPLETED)
                        .delta(1.0)
                        .occurredAt(occurredAt)
                        .build()
        ));
    }

    private static String getWeeklyEventTitle(WeeklyEventType eventType) {
        return switch (eventType) {
            case WEEKEND_TRADING_TOURNAMENT -> "주말 거래 토너먼트";
            case CHALLENGE_EVENT -> "챌린지 이벤트";
        };
    }
}
