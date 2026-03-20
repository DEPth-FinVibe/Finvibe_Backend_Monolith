package depth.finvibe.modules.gamification.infra.scheduler;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import depth.finvibe.modules.gamification.application.port.in.ChallengeCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.MetricCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.XpCommandUseCase;

@Component
@RequiredArgsConstructor
/**
 * KST 기준으로 주간 갱신 배치 작업을 실행합니다.
 */
public class GamificationScheduler {

    private final ChallengeCommandUseCase challengeCommandUseCase;
    private final MetricCommandUseCase metricCommandUseCase;
    private final XpCommandUseCase xpCommandUseCase;

    /**
     * 매주 월요일 00:00에 스쿼드 랭킹을 정산하고 주간 XP를 초기화합니다.
     */
    public void updateWeeklySquadRanking() {
        xpCommandUseCase.updateWeeklySquadRanking();
    }

    /**
     * 매주 일요일 23:55에 개인 챌린지 보상을 지급합니다.
     */
    public void rewardPersonalChallenges() {
        challengeCommandUseCase.rewardPersonalChallenges();
    }

    /**
     * 매주 일요일 23:58에 주간 이벤트 보상을 지급합니다.
     */
    public void rewardWeeklyChallenges() {
        challengeCommandUseCase.rewardWeeklyChallenges();
    }

    /**
     * 매주 월요일 00:05에 개인 챌린지를 생성합니다.
     */
    public void generatePersonalChallenges() {
        metricCommandUseCase.resetWeeklyMetrics();
        challengeCommandUseCase.generatePersonalChallenges();
    }

    /**
     * 10분마다 전체 사용자 주간/월간 XP 랭킹 스냅샷을 갱신합니다.
     */
    public void refreshUserRankingSnapshots() {
        xpCommandUseCase.refreshUserRankingSnapshots();
    }
}
