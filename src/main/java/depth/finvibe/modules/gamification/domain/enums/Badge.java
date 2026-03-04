package depth.finvibe.modules.gamification.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Getter
public enum Badge {
    FIRST_PROFIT("첫 수익"),
    KNOWLEDGE_SEEKER("지식 탐구자"),
    DILIGENT_INVESTOR("성실한 투자자"),
    DIVERSIFICATION_MASTER("분산 투자의 정석"),
    BEST_DEBATER("베스트 토론왕"),
    PERFECT_SCORE_QUIZ("퀴즈 백점 만점"),
    CHALLENGE_MARATHONER("챌린지 마라토너"),
    TOP_ONE_PERCENT_TRAINER("상위 1% 트레이너");

    private final String displayName;

    public static List<Badge> getAllBadges() {
        return Arrays.asList(values());
    }
}
